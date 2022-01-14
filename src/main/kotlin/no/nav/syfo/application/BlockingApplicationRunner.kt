package no.nav.syfo.application

import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.*
import no.nav.syfo.application.services.isNotLegevakt
import no.nav.syfo.application.services.startSubscription
import no.nav.syfo.client.*
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.handlestatus.*
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.MESSAGES_SENT_TO_BOQ
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.metrics.SAR_TSS_MISS_COUNTER
import no.nav.syfo.model.*
import no.nav.syfo.persistering.db.hentMottattTidspunkt
import no.nav.syfo.services.*
import no.nav.syfo.util.*
import no.nav.syfo.validation.isKodeverkValid
import java.io.StringReader
import java.time.ZoneId
import java.util.*
import javax.jms.*

class BlockingApplicationRunner(
    val applicationState: ApplicationState,
    val database: DatabaseInterface,
    val env: Environment,
    val inputconsumer: MessageConsumer,
    val session: Session,
    val receiptProducer: MessageProducer,
    val backoutProducer: MessageProducer,
    val arenaProducer: MessageProducer,
    val dialogmeldingProducer: DialogmeldingProducer,
    val subscriptionEmottak: SubscriptionPort,
) {
    val oidcClient = StsOidcClient(
        username = env.serviceuserUsername,
        password = env.serviceuserPassword,
        stsUrl = env.stsUrl,
    )

    val aktoerIdClient = AktoerIdClient(
        endpointUrl = env.aktoerregisterV1Url,
        stsClient = oidcClient,
        httpClient = httpClient,
        serviceUsername = env.serviceuserUsername,
    )

    val kuhrSarClient = SarClient(
        endpointUrl = env.kuhrSarApiUrl,
        httpClient = httpClient,
    )

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

    val dokArkivClient = DokArkivClient(
        azureAdV2Client = azureAdV2Client,
        dokArkivClientId = env.dokArkivClientId,
        url = env.dokArkivUrl,
        httpClient = httpClientWithTimeout,
    )

    val syfohelsenettproxyClient = SyfohelsenettproxyClient(
        azureAdV2Client = azureAdV2Client,
        endpointUrl = env.syfohelsenettproxyEndpointURL,
        httpClient = httpClient,
        helsenettClientId = env.helsenettClientId,
    )

    val padm2ReglerService = RuleService(
        legeSuspensjonClient = LegeSuspensjonClient(
            endpointUrl = env.legeSuspensjonEndpointURL,
            username = env.serviceuserUsername,
            stsClient = oidcClient,
            httpClient = httpClient,
        ),
        syfohelsenettproxyClient = syfohelsenettproxyClient,
    )

    val journalService = JournalService(
        dokArkivClient = dokArkivClient,
        pdfgenClient = pdfgenClient,
    )

    val signerendeLegeService = SignerendeLegeService(
        syfohelsenettproxyClient = syfohelsenettproxyClient,
    )

    suspend fun run() {
        wrapExceptions {
            while (applicationState.ready) {
                val message = inputconsumer.receiveNoWait()
                if (message == null) {
                    delay(100)
                    continue
                }
                processMessageHandleException(message)
            }
        }
    }

    suspend fun processMessageHandleException(message: Message) {
        try {
            val inputMessageText = when (message) {
                is TextMessage -> message.text
                else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
            }
            processMessage(inputMessageText)
        } catch (e: Exception) {
            backoutProducer.send(message)
            MESSAGES_SENT_TO_BOQ.inc()
            logger.error("Exception caught while handling message, sent to backout: {}", e.message)
        } catch (t: Throwable) {
            try {
                backoutProducer.send(message)
                MESSAGES_SENT_TO_BOQ.inc()
                logger.error("Error caught while handling message, sent to backout: {}", t.message)
            } finally {
                throw t
            }
        }
    }

    suspend fun processMessage(inputMessageText: String) {
        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
        val msgHead: XMLMsgHead = fellesformat.get()
        val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
        val ediLoggId = receiverBlock.ediLoggId
        val msgId = msgHead.msgInfo.msgId

        val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
        val innbyggerIdent = extractInnbyggerident(fellesformat)
        val legeIdent = receiverBlock.avsenderFnrFraDigSignatur
        val legekontorOrgName = extractSenderOrganisationName(fellesformat)
        val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
        val legekontorReshId = extractOrganisationReshNumberFromSender(fellesformat)?.id
        val dialogmeldingXml = extractDialogmelding(fellesformat)
        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)
        val vedlegg = extractVedlegg(fellesformat)
        val sha256String = sha256hashstring(dialogmeldingXml, vedlegg)
        val legeHpr = extractLegeHpr(fellesformat)
        val behandlerNavn = extractBehandlerNavn(fellesformat)
        val pasientNavn = extractPasientNavn(fellesformat)

        val requestLatency = REQUEST_TIME.startTimer()

        val loggingMeta = LoggingMeta(
            mottakId = ediLoggId,
            orgNr = legekontorOrgNr,
            msgId = msgHead.msgInfo.msgId,
        )

        logger.info("Received message, {}", StructuredArguments.fields(loggingMeta))

        INCOMING_MESSAGE_COUNTER.inc()

        if (innbyggerIdent.isNullOrEmpty()) {
            handlePatientNotFound(
                session, receiptProducer, fellesformat, env, loggingMeta,
            )
            return
        }

        val aktoerIds = aktoerIdClient.getAktoerIds(
            personIdenter = listOf(legeIdent, innbyggerIdent),
            loggingMeta = loggingMeta
        )
        val innbyggerAktoerIdents = aktoerIds[innbyggerIdent]
        val legeAktoerIdents = aktoerIds[legeIdent]

        val samhandlerPraksis = findSamhandlerpraksis(
            legeIdent = legeIdent,
            legekontorOrgName = legekontorOrgName,
            legekontorHerId = legekontorHerId,
            receiverBlock = receiverBlock,
            msgHead = msgHead,
            loggingMeta = loggingMeta,
        )

        val dialogmelding = dialogmeldingXml.toDialogmelding(
            dialogmeldingId = UUID.randomUUID().toString(),
            dialogmeldingType = dialogmeldingType,
            signaturDato = msgHead.msgInfo.genDate,
            navnHelsePersonellNavn = behandlerNavn
        )

        val receivedDialogmelding = ReceivedDialogmelding(
            dialogmelding = dialogmelding,
            personNrPasient = innbyggerIdent,
            pasientAktoerId = innbyggerAktoerIdents?.identer?.firstOrNull()?.ident,
            personNrLege = legeIdent,
            legeAktoerId = legeAktoerIdents?.identer?.firstOrNull()?.ident,
            navLogId = ediLoggId,
            msgId = msgId,
            legekontorOrgNr = legekontorOrgNr,
            legekontorOrgName = legekontorOrgName,
            legekontorHerId = legekontorHerId,
            legekontorReshId = legekontorReshId,
            mottattDato = receiverBlock.mottattDatotid.toGregorianCalendar().toZonedDateTime()
                .withZoneSameInstant(
                    ZoneId.of("Europe/Oslo")
                ).toLocalDateTime(),
            legehpr = legeHpr,
            fellesformat = inputMessageText,
            tssid = samhandlerPraksis?.tss_ident ?: ""
        )

        val navnSignerendeLege = signerendeLegeService.signerendeLegeNavn(legeIdent, msgId, loggingMeta)

        val validationResult = validateMessage(
            sha256String = sha256String,
            loggingMeta = loggingMeta,
            innbyggerAktoerIdents = innbyggerAktoerIdents,
            legeAktoerIdents = legeAktoerIdents,
            innbyggerIdent = innbyggerIdent,
            dialogmeldingType = dialogmeldingType,
            dialogmeldingXml = dialogmeldingXml,
            receivedDialogmelding = receivedDialogmelding,
        )

        when (validationResult.status) {
            Status.OK -> handleStatusOK(
                session,
                receiptProducer,
                fellesformat,
                loggingMeta,
                env.apprecQueueName,
                journalService,
                dialogmeldingProducer,
                receivedDialogmelding,
                validationResult,
                vedlegg.map { it.toVedlegg() },
                arenaProducer,
                msgHead,
                receiverBlock,
                dialogmelding,
                database,
                pasientNavn,
                navnSignerendeLege,
                sha256String,
            )

            Status.INVALID -> handleStatusINVALID(
                validationResult,
                session,
                receiptProducer,
                fellesformat,
                loggingMeta,
                env.apprecQueueName,
                journalService,
                receivedDialogmelding,
                vedlegg.map { it.toVedlegg() },
                database,
                pasientNavn,
                navnSignerendeLege,
                sha256String,
            )
        }

        val currentRequestLatency = requestLatency.observeDuration()

        logger.info(
            "Finished message got outcome {}, {}, processing took {}s",
            StructuredArguments.keyValue("status", validationResult.status),
            StructuredArguments.keyValue(
                "ruleHits",
                validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }
            ),
            StructuredArguments.keyValue("latency", currentRequestLatency),
            StructuredArguments.fields(loggingMeta)
        )
    }

    suspend fun validateMessage(
        sha256String: String,
        loggingMeta: LoggingMeta,
        innbyggerAktoerIdents: IdentInfoResult?,
        legeAktoerIdents: IdentInfoResult?,
        innbyggerIdent: String,
        dialogmeldingType: DialogmeldingType,
        dialogmeldingXml: XMLDialogmelding,
        receivedDialogmelding: ReceivedDialogmelding,
    ): ValidationResult {
        val initialValidationResult = if (dialogmeldingDokumentWithShaExists(sha256String, database)) {
            val tidMottattOpprinneligMelding = database.connection.hentMottattTidspunkt(sha256String)
            handleDuplicateDialogmeldingContent(
                loggingMeta, sha256String, tidMottattOpprinneligMelding
            )
        } else if (innbyggerAktoerIdents == null || innbyggerAktoerIdents.feilmelding != null) {
            handlePatientNotFoundInAktorRegister(
                innbyggerAktoerIdents,
                loggingMeta,
            )
        } else if (legeAktoerIdents == null || legeAktoerIdents.feilmelding != null) {
            handleDoctorNotFoundInAktorRegister(
                legeAktoerIdents,
                loggingMeta,
            )
        } else if (erTestFnr(innbyggerIdent) && env.cluster == "prod-fss") {
            handleTestFnrInProd(loggingMeta)
        } else if (dialogmeldingType.isHenvendelseFraLegeOrForesporselSvar() && dialogmeldingXml.notat.first().tekstNotatInnhold.isNullOrEmpty()) {
            handleMeldingsTekstMangler(loggingMeta)
        } else if (!isKodeverkValid(dialogmeldingXml, dialogmeldingType)) {
            handleInvalidDialogMeldingKodeverk(loggingMeta)
        } else {
            null
        }
        return if (initialValidationResult != null) {
            ValidationResult(
                status = Status.INVALID,
                apprecMessage = initialValidationResult,
                ruleHits = emptyList(),
            )
        } else {
            padm2ReglerService.executeRuleChains(
                receivedDialogmelding = receivedDialogmelding,
            )
        }
    }

    suspend fun findSamhandlerpraksis(
        legeIdent: String,
        legekontorOrgName: String,
        legekontorHerId: String?,
        receiverBlock: XMLMottakenhetBlokk,
        msgHead: XMLMsgHead,
        loggingMeta: LoggingMeta,
    ): SamhandlerPraksis? {
        val samhandlerInfo = kuhrSarClient.getSamhandler(legeIdent)
        val samhandlerPraksisMatch = findBestSamhandlerPraksis(
            samhandlerInfo,
            legekontorOrgName,
            legekontorHerId,
            loggingMeta
        )

        val samhandlerPraksis = samhandlerPraksisMatch?.samhandlerPraksis

        if (samhandlerPraksisMatch?.percentageMatch != null && samhandlerPraksisMatch.percentageMatch == 999.0) {
            logger.info(
                "SamhandlerPraksis is found but is FALE or FALO, subscription_emottak is not created, {}",
                StructuredArguments.fields(loggingMeta)
            )
        } else {

            when (samhandlerPraksis) {
                null -> {
                    logger.info(
                        "SamhandlerPraksis is Not found, {}",
                        StructuredArguments.fields(loggingMeta)
                    )
                    SAR_TSS_MISS_COUNTER.inc()
                }
                else -> if (isNotLegevakt(samhandlerPraksis) &&
                    !receiverBlock.partnerReferanse.isNullOrEmpty() &&
                    receiverBlock.partnerReferanse.isNotBlank()
                ) {
                    startSubscription(
                        subscriptionEmottak,
                        samhandlerPraksis,
                        msgHead,
                        receiverBlock,
                        loggingMeta
                    )
                } else {
                    logger.info(
                        "SamhandlerPraksis is Legevakt or partnerReferanse is empty or blank, subscription_emottak is not created, {}",
                        StructuredArguments.fields(loggingMeta)
                    )
                }
            }
        }
        return samhandlerPraksis
    }
}
