package no.nav.syfo.application

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.Environment
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.*
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.handlestatus.*
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.logger
import no.nav.syfo.metrics.COUNT_TSS_SAR_SMTSS_DIFFERENT
import no.nav.syfo.metrics.COUNT_TSS_SAR_SMTSS_MATCH
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.*
import no.nav.syfo.persistering.db.hentMottattTidspunkt
import no.nav.syfo.persistering.persistRecivedMessageValidation
import no.nav.syfo.services.*
import no.nav.syfo.util.*
import no.nav.syfo.validation.isKodeverkValid
import java.io.StringReader
import java.time.Duration
import java.time.ZoneId

class DialogmeldingProcessor(
    val database: DatabaseInterface,
    val env: Environment,
    val mqSender: MQSenderInterface,
    val dialogmeldingProducer: DialogmeldingProducer,
) {
    val pdfgenClient = PdfgenClient(
        url = env.syfopdfgen,
        httpClient = httpClient,
    )
    val azureAdV2Client = AzureAdV2Client(
        aadAppClient = env.aadAppClient,
        aadAppSecret = env.aadAppSecret,
        aadTokenEndpoint = env.aadTokenEndpoint,
        httpClient = httpClientWithProxy,
    )
    val kuhrSarClient = KuhrSarClient(
        azureAdV2Client = azureAdV2Client,
        kuhrSarClientId = env.kuhrSarApiClientId,
        kuhrSarUrl = env.kuhrSarApiUrl,
        httpClient = httpClient,
    )
    val smtssClient = SmtssClient(
        azureAdV2Client = azureAdV2Client,
        smtssClientId = env.smtssClientId,
        smtssUrl = env.smtssApiUrl,
        httpClient = httpClient,
    )
    val pdlClient = PdlClient(
        azureAdV2Client = azureAdV2Client,
        pdlClientId = env.pdlClientId,
        pdlUrl = env.pdlUrl,
    )
    val dokArkivClient = DokArkivClient(
        azureAdV2Client = azureAdV2Client,
        dokArkivClientId = env.dokArkivClientId,
        url = env.dokArkivUrl,
    )
    val syfohelsenettproxyClient = SyfohelsenettproxyClient(
        azureAdV2Client = azureAdV2Client,
        endpointUrl = env.syfohelsenettproxyEndpointURL,
        httpClient = httpClient,
        helsenettClientId = env.syfohelsenettproxyClientId,
    )
    val legeSuspensjonClient = LegeSuspensjonClient(
        azureAdV2Client = azureAdV2Client,
        endpointUrl = env.legeSuspensjonEndpointURL,
        endpointClientId = env.legeSuspensjonClientId,
        httpClient = httpClient,
    )
    val padm2ReglerService = RuleService(
        legeSuspensjonClient = legeSuspensjonClient,
        syfohelsenettproxyClient = syfohelsenettproxyClient,
    )
    val journalService = JournalService(
        dokArkivClient = dokArkivClient,
        pdfgenClient = pdfgenClient,
        database = database,
    )
    val signerendeLegeService = SignerendeLegeService(
        syfohelsenettproxyClient = syfohelsenettproxyClient,
    )
    val clamAvClient = ClamAvClient(
        endpointUrl = env.clamavURL,
    )
    val virusScanService = VirusScanService(
        clamAvClient = clamAvClient,
    )

    suspend fun processMessage(
        dialogmeldingId: String,
        inputMessageText: String,
    ) {
        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
        val msgHead: XMLMsgHead = fellesformat.get()
        val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
        val ediLoggId = receiverBlock.ediLoggId
        val msgId = msgHead.msgInfo.msgId
        val dialogmeldingXml = extractDialogmelding(fellesformat)
        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)
        val xmlVedlegg = extractVedlegg(fellesformat)
        val sha256String = sha256hashstring(dialogmeldingXml, xmlVedlegg)
        val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
        val pasientNavn = extractPasientNavn(fellesformat)

        val loggingMeta = LoggingMeta(
            mottakId = ediLoggId,
            orgNr = legekontorOrgNr,
            msgId = msgHead.msgInfo.msgId,
        )
        val starttime = System.currentTimeMillis()

        val receivedDialogmelding = createReceivedDialogmelding(
            dialogmeldingId = dialogmeldingId,
            fellesformat = fellesformat,
            inputMessageText = inputMessageText,
        )

        val innbyggerOK = pdlClient.personEksisterer(PersonIdent(receivedDialogmelding.personNrPasient))
        val legeOK = pdlClient.personEksisterer(PersonIdent(receivedDialogmelding.personNrLege))

        val tssId = getTssId(
            receivedDialogmelding = receivedDialogmelding,
            receiverBlock = receiverBlock,
            msgHead = msgHead,
        )

        val navnSignerendeLege = signerendeLegeService.signerendeLegeNavn(
            signerendeLegeFnr = receivedDialogmelding.personNrLege,
            msgId = msgId,
            loggingMeta = loggingMeta,
        )
        val vedleggListe = xmlVedlegg.map { xmlVedlegg -> xmlVedlegg.toVedlegg() }

        val validationResult = validateMessage(
            sha256String = sha256String,
            loggingMeta = loggingMeta,
            innbyggerOK = innbyggerOK,
            legeOK = legeOK,
            dialogmeldingType = dialogmeldingType,
            dialogmeldingXml = dialogmeldingXml,
            receivedDialogmelding = receivedDialogmelding,
            vedlegg = vedleggListe,
        )

        when (validationResult.status) {
            Status.OK -> handleStatusOK(
                database = database,
                mqSender = mqSender,
                fellesformat = fellesformat,
                loggingMeta = loggingMeta,
                journalService = journalService,
                dialogmeldingProducer = dialogmeldingProducer,
                receivedDialogmelding = receivedDialogmelding,
                validationResult = validationResult,
                vedleggListe = vedleggListe,
                msgHead = msgHead,
                receiverBlock = receiverBlock,
                pasientNavn = pasientNavn,
                navnSignerendeLege = navnSignerendeLege,
                tssId = tssId,
            )

            Status.INVALID -> handleStatusINVALID(
                database = database,
                mqSender = mqSender,
                validationResult = validationResult,
                fellesformat = fellesformat,
                loggingMeta = loggingMeta,
                journalService = journalService,
                receivedDialogmelding = receivedDialogmelding,
                vedleggListe = vedleggListe,
                pasientNavn = pasientNavn,
                navnSignerendeLege = navnSignerendeLege,
                innbyggerOK = innbyggerOK,
            )
        }

        persistRecivedMessageValidation(
            receivedDialogmelding = receivedDialogmelding,
            validationResult = validationResult,
            database = database,
        )

        val duration = Duration.ofMillis(System.currentTimeMillis() - starttime)
        REQUEST_TIME.record(duration)

        logger.info(
            "Finished message got outcome {}, {}, processing took {} ms",
            StructuredArguments.keyValue("status", validationResult.status),
            StructuredArguments.keyValue(
                "ruleHits",
                validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }
            ),
            StructuredArguments.keyValue("latency", duration.toMillis()),
            StructuredArguments.fields(loggingMeta)
        )
    }

    fun createReceivedDialogmelding(
        dialogmeldingId: String,
        fellesformat: XMLEIFellesformat,
        inputMessageText: String,
    ): ReceivedDialogmelding {
        val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
        val msgHead: XMLMsgHead = fellesformat.get()
        val legeIdent = receiverBlock.avsenderFnrFraDigSignatur
        val legekontorOrgName = extractSenderOrganisationName(fellesformat)
        val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
        val dialogmeldingXml = extractDialogmelding(fellesformat)
        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)
        val legeHpr = extractLegeHpr(dialogmeldingId, fellesformat)
        val behandlerNavn = extractBehandlerNavn(fellesformat)
        val behandlerIdent = extractIdentFromBehandler(fellesformat)
        val innbyggerIdent = extractInnbyggerident(fellesformat)
        val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id

        if (behandlerIdent != legeIdent) {
            logger.info("Behandler and avsender are different in dialogmelding: $dialogmeldingId")
        }

        val dialogmelding = dialogmeldingXml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = msgHead.msgInfo.genDate,
            navnHelsePersonellNavn = behandlerNavn
        )

        return ReceivedDialogmelding(
            dialogmelding = dialogmelding,
            personNrPasient = innbyggerIdent!!,
            personNrLege = legeIdent,
            navLogId = receiverBlock.ediLoggId,
            msgId = msgHead.msgInfo.msgId,
            legekontorOrgNr = legekontorOrgNr,
            legekontorOrgName = legekontorOrgName,
            legekontorHerId = legekontorHerId,
            mottattDato = receiverBlock.mottattDatotid.toGregorianCalendar().toZonedDateTime()
                .withZoneSameInstant(
                    ZoneId.of("Europe/Oslo")
                ).toLocalDateTime(),
            legehpr = legeHpr,
            fellesformat = inputMessageText,
        )
    }

    suspend fun validateMessage(
        sha256String: String,
        loggingMeta: LoggingMeta,
        innbyggerOK: Boolean,
        legeOK: Boolean,
        dialogmeldingType: DialogmeldingType,
        dialogmeldingXml: XMLDialogmelding,
        receivedDialogmelding: ReceivedDialogmelding,
        vedlegg: List<Vedlegg>,
    ): ValidationResult {
        val initialValidationResult: ValidationResult? =
            if (dialogmeldingDokumentWithShaExists(receivedDialogmelding.dialogmelding.id, sha256String, database)) {
                val tidMottattOpprinneligMelding = database.hentMottattTidspunkt(sha256String)
                handleDuplicateDialogmeldingContent(
                    loggingMeta,
                    sha256String,
                    tidMottattOpprinneligMelding,
                )
            } else if (!innbyggerOK) {
                handlePatientNotFound(loggingMeta)
            } else if (!legeOK) {
                handleBehandlerNotFound(loggingMeta)
            } else if (erTestFnr(receivedDialogmelding.personNrPasient) && env.cluster == "prod-gcp") {
                handleTestFnrInProd(loggingMeta)
            } else if (dialogmeldingType.isHenvendelseFraLegeOrForesporselSvar() && dialogmeldingXml.notat.first().tekstNotatInnhold.isNullOrEmpty()) {
                handleMeldingsTekstMangler(loggingMeta)
            } else if (!isKodeverkValid(dialogmeldingXml, dialogmeldingType)) {
                handleInvalidDialogMeldingKodeverk(loggingMeta)
            } else if (virusScanService.vedleggContainsVirus(vedlegg)) {
                handleVedleggMayContainVirus(loggingMeta)
            } else {
                null
            }

        return initialValidationResult ?: padm2ReglerService.executeRuleChains(
            receivedDialogmelding = receivedDialogmelding,
        )
    }

    private suspend fun getTssId(
        receivedDialogmelding: ReceivedDialogmelding,
        receiverBlock: XMLMottakenhetBlokk,
        msgHead: XMLMsgHead
    ): String {
        val tssId = kuhrSarClient.getTssId(
            legeIdent = PersonIdent(receivedDialogmelding.personNrLege),
            partnerId = receiverBlock.partnerReferanse.toInt(),
            legekontorOrgName = receivedDialogmelding.legekontorOrgName,
            legekontorHerId = receivedDialogmelding.legekontorHerId,
            msgHead = msgHead,
        )
        val smtssEmottak = smtssClient.findBestTssIdEmottak(
            legePersonIdent = PersonIdent(receivedDialogmelding.personNrLege),
            legekontorOrgName = receivedDialogmelding.legekontorOrgName,
            dialogmeldingId = receivedDialogmelding.msgId,
        )
        val smtssInfotrygd = smtssClient.findBestTssIdInfotrygd(
            legePersonIdent = PersonIdent(receivedDialogmelding.personNrLege),
            legekontorOrgName = receivedDialogmelding.legekontorOrgName,
            dialogmeldingId = receivedDialogmelding.msgId,
        )

        logger.info("TSS TRACE: Kuhr-sar: ${nonMaskableTssString(tssId)}, smtssEmottak: ${nonMaskableTssString(smtssEmottak)}, smtssInfotrygd: ${nonMaskableTssString(smtssInfotrygd)}")
        if (tssId == smtssEmottak || tssId == smtssInfotrygd) {
            COUNT_TSS_SAR_SMTSS_MATCH.increment()
        } else {
            COUNT_TSS_SAR_SMTSS_DIFFERENT.increment()
        }

        return tssId
    }

    private fun nonMaskableTssString(tssId: String?): String? {
        if (tssId == null || tssId.length < 11) return tssId

        val firstPart = tssId.substring(0, 5)
        val lastPart = tssId.substring(5)
        return "$firstPart-$lastPart"
    }
}
