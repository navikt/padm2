package no.nav.syfo.application

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.dialogmelding.XMLDialogmelding
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
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.*
import no.nav.syfo.persistering.db.hentMottattTidspunkt
import no.nav.syfo.persistering.persistRecivedMessageValidation
import no.nav.syfo.services.*
import no.nav.syfo.util.*
import no.nav.syfo.validation.isKodeverkValid
import java.time.Duration

class DialogmeldingProcessor(
    val database: DatabaseInterface,
    val env: Environment,
    val mqSender: MQSenderInterface,
    val dialogmeldingProducer: DialogmeldingProducer,
    val azureAdV2Client: AzureAdV2Client,
) {
    val pdfgenClient = PdfgenClient(
        url = env.syfopdfgen,
        httpClient = httpClientPdfgen,
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
        httpClient = httpClientRetryAll,
        helsenettClientId = env.syfohelsenettproxyClientId,
    )
    val legeSuspensjonClient = LegeSuspensjonClient(
        azureAdV2Client = azureAdV2Client,
        endpointUrl = env.legeSuspensjonEndpointURL,
        endpointClientId = env.legeSuspensjonClientId,
        applicationName = env.applicationName,
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
        jpRetryEnabled = env.jpRetryEnabled,
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

    suspend fun process(
        dialogmeldingId: String,
        inputMessageText: String,
    ) {
        val fellesformat = safeUnmarshal(inputMessageText)
        val msgHead: XMLMsgHead = fellesformat.get()
        val emottakblokk = fellesformat.get<XMLMottakenhetBlokk>()
        val msgId = msgHead.msgInfo.msgId
        val dialogmeldingXml = extractDialogmelding(fellesformat)
        val patientXml = extractPatient(fellesformat)
        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)
        val xmlVedlegg = extractValidVedlegg(fellesformat)
        val sha256String = sha256hashstring(dialogmeldingXml, patientXml, xmlVedlegg)
        val pasientNavn = extractPasientNavn(fellesformat)

        val loggingMeta = LoggingMeta.create(
            emottakBlokk = emottakblokk,
            fellesformatXml = fellesformat,
            msgHead = msgHead,
        )
        val starttime = System.currentTimeMillis()

        val receivedDialogmelding = ReceivedDialogmelding.create(
            dialogmeldingId = dialogmeldingId,
            fellesformat = fellesformat,
            inputMessageText = inputMessageText,
        )

        val innbyggerOK = pdlClient.personEksisterer(PersonIdent(receivedDialogmelding.personNrPasient))
        val legeOK = pdlClient.personEksisterer(PersonIdent(receivedDialogmelding.personNrLege))

        val navnSignerendeLege = signerendeLegeService.signerendeLegeNavn(
            signerendeLegeFnr = receivedDialogmelding.personNrLege,
            msgId = msgId,
            loggingMeta = loggingMeta,
        )
        val vedleggListe = xmlVedlegg.map { xml -> xml.toVedlegg() }

        val validationResult = validateMessage(
            msgId = msgId,
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
                pasientNavn = pasientNavn,
                navnSignerendeLege = navnSignerendeLege,
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

    suspend fun validateMessage(
        msgId: String,
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
            } else if (!isKodeverkValid(msgId, dialogmeldingXml, dialogmeldingType)) {
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
}
