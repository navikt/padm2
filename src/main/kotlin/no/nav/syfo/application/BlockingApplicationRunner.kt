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
import no.nav.syfo.persistering.persistRecivedMessageValidation
import no.nav.syfo.persistering.persistReceivedMessage
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
        database = database,
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
        val inputMessageText = when (message) {
            is TextMessage -> message.text
            else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
        }
        val dialogmeldingId: String? = try {
            storeMessage(inputMessageText)
        } catch (e: Exception) {
            backoutProducer.send(message)
            MESSAGES_SENT_TO_BOQ.inc()
            logger.error("Exception caught while storing message, sent to backout: {}", e.message)
            null
        } catch (t: Throwable) {
            try {
                backoutProducer.send(message)
                MESSAGES_SENT_TO_BOQ.inc()
                logger.error("Error caught while storing message, sent to backout: {}", t.message)
                null
            } finally {
                throw t
            }
        }

        try {
            if (inputMessageText != null && dialogmeldingId != null) {
                processMessage(dialogmeldingId, inputMessageText)
            }
        } catch (e: Exception) {
            logger.warn("Exception caught while processing message, will try again later: {}", e.message)
        }
    }

    fun storeMessage(
        inputMessageText: String
    ): String? {
        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
        val msgHead: XMLMsgHead = fellesformat.get()
        val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
        val ediLoggId = receiverBlock.ediLoggId
        val dialogmeldingXml = extractDialogmelding(fellesformat)
        val vedlegg = extractVedlegg(fellesformat)
        val sha256String = sha256hashstring(dialogmeldingXml, vedlegg)

        val innbyggerIdent = extractInnbyggerident(fellesformat)
        val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
        val loggingMeta = LoggingMeta(
            mottakId = ediLoggId,
            orgNr = legekontorOrgNr,
            msgId = msgHead.msgInfo.msgId,
        )
        logger.info("Received message, {}", StructuredArguments.fields(loggingMeta))
        if (innbyggerIdent.isNullOrEmpty()) {
            handlePatientNotFound(
                session, receiptProducer, fellesformat, env, loggingMeta,
            )
            return null
        }

        val dialogmeldingId = UUID.randomUUID().toString()

        val receivedDialogmelding = createReceivedDialogmelding(
            dialogmeldingId = dialogmeldingId,
            fellesformat = fellesformat,
            inputMessageText = inputMessageText,
        )

        persistReceivedMessage(
            receivedDialogmelding = receivedDialogmelding,
            sha256String = sha256String,
            loggingMeta = loggingMeta,
            database = database,
        )
        INCOMING_MESSAGE_COUNTER.inc()
        return dialogmeldingId
    }

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
        val vedlegg = extractVedlegg(fellesformat)
        val sha256String = sha256hashstring(dialogmeldingXml, vedlegg)
        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)
        val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
        val pasientNavn = extractPasientNavn(fellesformat)

        val loggingMeta = LoggingMeta(
            mottakId = ediLoggId,
            orgNr = legekontorOrgNr,
            msgId = msgHead.msgInfo.msgId,
        )
        val requestLatency = REQUEST_TIME.startTimer()

        val receivedDialogmelding = createReceivedDialogmelding(
            dialogmeldingId = dialogmeldingId,
            fellesformat = fellesformat,
            inputMessageText = inputMessageText,
        )
        val aktoerIds = aktoerIdClient.getAktoerIds(
            personIdenter = listOf(receivedDialogmelding.personNrLege, receivedDialogmelding.personNrPasient),
            loggingMeta = loggingMeta
        )

        val innbyggerAktoerIdents = aktoerIds[receivedDialogmelding.personNrPasient]
        val legeAktoerIdents = aktoerIds[receivedDialogmelding.personNrLege]

        val samhandlerPraksis = findSamhandlerpraksis(
            legeIdent = receivedDialogmelding.personNrLege,
            legekontorOrgName = receivedDialogmelding.legekontorOrgName,
            legekontorHerId = receivedDialogmelding.legekontorHerId,
            receiverBlock = receiverBlock,
            msgHead = msgHead,
            loggingMeta = loggingMeta,
        )

        val navnSignerendeLege = signerendeLegeService.signerendeLegeNavn(
            signerendeLegeFnr = receivedDialogmelding.personNrLege,
            msgId = msgId,
            loggingMeta = loggingMeta,
        )

        val validationResult = validateMessage(
            sha256String = sha256String,
            loggingMeta = loggingMeta,
            innbyggerAktoerIdents = innbyggerAktoerIdents,
            legeAktoerIdents = legeAktoerIdents,
            dialogmeldingType = dialogmeldingType,
            dialogmeldingXml = dialogmeldingXml,
            receivedDialogmelding = receivedDialogmelding,
        )

        when (validationResult.status) {
            Status.OK -> handleStatusOK(
                session = session,
                database = database,
                receiptProducer = receiptProducer,
                fellesformat = fellesformat,
                loggingMeta = loggingMeta,
                apprecQueueName = env.apprecQueueName,
                journalService = journalService,
                dialogmeldingProducer = dialogmeldingProducer,
                receivedDialogmelding = receivedDialogmelding,
                validationResult = validationResult,
                vedleggListe = vedlegg.map { it.toVedlegg() },
                arenaProducer = arenaProducer,
                msgHead = msgHead,
                receiverBlock = receiverBlock,
                pasientNavn = pasientNavn,
                navnSignerendeLege = navnSignerendeLege,
                samhandlerPraksis = samhandlerPraksis,
                pasientAktoerId = innbyggerAktoerIdents!!.identer!!.first().ident,
                legeAktoerId = legeAktoerIdents!!.identer!!.first().ident,
            )

            Status.INVALID -> handleStatusINVALID(
                validationResult = validationResult,
                session = session,
                database = database,
                receiptProducer = receiptProducer,
                fellesformat = fellesformat,
                loggingMeta = loggingMeta,
                apprecQueueName = env.apprecQueueName,
                journalService = journalService,
                receivedDialogmelding = receivedDialogmelding,
                vedleggListe = vedlegg.map { it.toVedlegg() },
                pasientNavn = pasientNavn,
                navnSignerendeLege = navnSignerendeLege,
                innbyggerAktoerIdent = innbyggerAktoerIdents?.identer?.firstOrNull()?.ident,
            )
        }

        persistRecivedMessageValidation(
            receivedDialogmelding = receivedDialogmelding,
            validationResult = validationResult,
            database = database,
        )

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

    private fun createReceivedDialogmelding(
        dialogmeldingId: String,
        fellesformat: XMLEIFellesformat,
        inputMessageText: String,
    ): ReceivedDialogmelding {
        val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
        val msgHead: XMLMsgHead = fellesformat.get()
        val legeIdent = receiverBlock.avsenderFnrFraDigSignatur
        val legekontorOrgName = extractSenderOrganisationName(fellesformat)
        val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
        val legekontorReshId = extractOrganisationReshNumberFromSender(fellesformat)?.id
        val dialogmeldingXml = extractDialogmelding(fellesformat)
        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)
        val legeHpr = extractLegeHpr(fellesformat)
        val behandlerNavn = extractBehandlerNavn(fellesformat)
        val innbyggerIdent = extractInnbyggerident(fellesformat)
        val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id

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
            legekontorReshId = legekontorReshId,
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
        innbyggerAktoerIdents: IdentInfoResult?,
        legeAktoerIdents: IdentInfoResult?,
        dialogmeldingType: DialogmeldingType,
        dialogmeldingXml: XMLDialogmelding,
        receivedDialogmelding: ReceivedDialogmelding,
    ): ValidationResult {
        val initialValidationResult =
            if (dialogmeldingDokumentWithShaExists(receivedDialogmelding.dialogmelding.id, sha256String, database)) {
                val tidMottattOpprinneligMelding = database.hentMottattTidspunkt(sha256String)
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
            } else if (erTestFnr(receivedDialogmelding.personNrPasient) && env.cluster == "prod-fss") {
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
