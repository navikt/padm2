package no.nav.syfo.application

import io.ktor.util.*
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.helse.base64container.Base64Container
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLDocument
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.application.services.isNotLegevakt
import no.nav.syfo.application.services.startSubscription
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.db.Database
import no.nav.syfo.handlestatus.*
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.MESSAGES_SENT_TO_BOQ
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.metrics.SAR_TSS_MISS_COUNTER
import no.nav.syfo.model.*
import no.nav.syfo.services.*
import no.nav.syfo.util.*
import no.nav.syfo.validation.isKodeverkValid
import java.io.StringReader
import java.time.ZoneOffset
import java.util.*
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage

class BlockingApplicationRunner {

    @KtorExperimentalAPI
    suspend fun run(
        applicationState: ApplicationState,
        inputconsumer: MessageConsumer,
        session: Session,
        env: Environment,
        secrets: VaultSecrets,
        aktoerIdClient: AktoerIdClient,
        kuhrSarClient: SarClient,
        subscriptionEmottak: SubscriptionPort,
        receiptProducer: MessageProducer,
        padm2ReglerService: RuleService,
        backoutProducer: MessageProducer,
        journalService: JournalService,
        arenaProducer: MessageProducer,
        database: Database,
        signerendeLegeService: SignerendeLegeService
    ) {
        wrapExceptions {
            loop@ while (applicationState.ready) {
                val message = inputconsumer.receiveNoWait()
                if (message == null) {
                    delay(100)
                    continue
                }

                try {
                    val inputMessageText = when (message) {
                        is TextMessage -> message.text
                        else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
                    }
                    val fellesformat =
                        fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
                    val msgHead: XMLMsgHead = fellesformat.get()
                    val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
                    val ediLoggId = receiverBlock.ediLoggId
                    val msgId = msgHead.msgInfo.msgId

                    log.info("Processing message with ediLoggId $ediLoggId, and msgId $msgId")

                    val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
                    val personNumberPatient = msgHead.msgInfo.patient.ident.find { it.typeId.v == "FNR" }?.id
                    val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur

                    val legekontorOrgName = extractSenderOrganisationName(fellesformat)
                    val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
                    val legekontorReshId = extractOrganisationReshNumberFromSender(fellesformat)?.id
                    val dialogmeldingXml = extractDialogmelding(fellesformat)
                    val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)
                    val sha256String = sha256hashstring(dialogmeldingXml)
                    val legeHpr = extractLegeHpr(fellesformat)

                    val behandlerNavn = extractBehandlerNavn(fellesformat)
                    val extractVedlegg = extractVedlegg(fellesformat)

                    if (!extractVedlegg.isEmpty()){
                        log.info("Dialogmelding has vedlegg")
                    }

                    val pasientNavn = extractPasientNavn(fellesformat)
                    val vedleggListe = extractVedlegg.map { it.toVedlegg() }

                    val requestLatency = REQUEST_TIME.startTimer()

                    val loggingMeta = LoggingMeta(
                        mottakId = ediLoggId,
                        orgNr = legekontorOrgNr,
                        msgId = msgHead.msgInfo.msgId,
                    )

                    log.info("Received message, {}", StructuredArguments.fields(loggingMeta))

                    val navnSignerendeLege = signerendeLegeService.signerendeLegeNavn(personNumberDoctor, msgId, loggingMeta)

                    INCOMING_MESSAGE_COUNTER.inc()

                    if (personNumberPatient.isNullOrEmpty()) {
                        handlePatientNotFound(
                            session, receiptProducer, fellesformat, env, loggingMeta
                        )
                        continue@loop
                    } else {

                        val aktoerIds = aktoerIdClient.getAktoerIds(
                            listOf(personNumberDoctor, personNumberPatient),
                            secrets.serviceuserUsername, loggingMeta
                        )

                        val samhandlerInfo = kuhrSarClient.getSamhandler(personNumberDoctor)
                        val samhandlerPraksisMatch = findBestSamhandlerPraksis(
                            samhandlerInfo,
                            legekontorOrgName,
                            legekontorHerId,
                            loggingMeta
                        )

                        val samhandlerPraksis = samhandlerPraksisMatch?.samhandlerPraksis

                        if (samhandlerPraksisMatch?.percentageMatch != null && samhandlerPraksisMatch.percentageMatch == 999.0) {
                            log.info(
                                "SamhandlerPraksis is found but is FALE or FALO, subscription_emottak is not created, {}",
                                StructuredArguments.fields(loggingMeta)
                            )
                        } else {

                            when (samhandlerPraksis) {
                                null -> {
                                    log.info(
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
                                    log.info(
                                        "SamhandlerPraksis is Legevakt or partnerReferanse is empty or blank, subscription_emottak is not created, {}",
                                        StructuredArguments.fields(loggingMeta)
                                    )
                                }
                            }
                        }

                        if (dialogmeldingDokumentWithShaExists(sha256String, database)) {
                            handleDuplicateDialogmeldingContent(
                                session, receiptProducer,
                                fellesformat, loggingMeta, env, sha256String
                            )
                            continue@loop
                        }

                        val patientIdents = aktoerIds[personNumberPatient]
                        val doctorIdents = aktoerIds[personNumberDoctor]

                        if (patientIdents == null || patientIdents.feilmelding != null) {
                            handlePatientNotFoundInAktorRegister(
                                patientIdents, session,
                                receiptProducer, fellesformat, env, loggingMeta
                            )
                            continue@loop
                        }
                        if (doctorIdents == null || doctorIdents.feilmelding != null) {
                            handleDoctorNotFoundInAktorRegister(
                                doctorIdents, session,
                                receiptProducer, fellesformat, env, loggingMeta
                            )
                            continue@loop
                        }
                        if (erTestFnr(personNumberPatient) && env.cluster == "prod-fss") {
                            handleTestFnrInProd(
                                session, receiptProducer, fellesformat, env, loggingMeta
                            )
                            continue@loop
                        }
                        if ((dialogmeldingType == DialogmeldingType.DIALOGMELDING_HENVENDELSE_FRA_LEGE_HENDVENDELSE ||
                                    dialogmeldingType == DialogmeldingType.DIALOGMELDING_FORESPORSEL_FRA_SAKSBEHANDLER_FORESPORSEL_SVAR) &&
                            dialogmeldingXml.notat.first().tekstNotatInnhold.isNullOrEmpty()
                        ) {
                            handleMeldingsTekstMangler(
                                session, receiptProducer, fellesformat, env, loggingMeta
                            )
                            continue@loop
                        }

                        if (!isKodeverkValid(dialogmeldingXml, dialogmeldingType)) {
                            handleInvalidDialogMeldingKodeverk(
                                session, receiptProducer, fellesformat, env, loggingMeta
                            )
                            continue@loop
                        }

                        val dialogmelding = dialogmeldingXml.toDialogmelding(
                            dialogmeldingId = UUID.randomUUID().toString(),
                            dialogmeldingType = dialogmeldingType,
                            signaturDato = msgHead.msgInfo.genDate,
                            navnHelsePersonellNavn = behandlerNavn
                        )

                        val receivedDialogmelding = ReceivedDialogmelding(
                            dialogmelding = dialogmelding,
                            personNrPasient = personNumberPatient,
                            pasientAktoerId = patientIdents.identer!!.first().ident,
                            personNrLege = personNumberDoctor,
                            legeAktoerId = doctorIdents.identer!!.first().ident,
                            navLogId = ediLoggId,
                            msgId = msgId,
                            legekontorOrgNr = legekontorOrgNr,
                            legekontorOrgName = legekontorOrgName,
                            legekontorHerId = legekontorHerId,
                            legekontorReshId = legekontorReshId,
                            mottattDato = receiverBlock.mottattDatotid.toGregorianCalendar().toZonedDateTime()
                                .withZoneSameInstant(
                                    ZoneOffset.UTC
                                ).toLocalDateTime(),
                            legehpr = legeHpr,
                            fellesformat = inputMessageText,
                            tssid = samhandlerPraksis?.tss_ident ?: ""
                        )

                        val validationResult = padm2ReglerService.executeRuleChains(receivedDialogmelding)

                        when (validationResult.status) {
                            Status.OK -> handleStatusOK(
                                session,
                                receiptProducer,
                                fellesformat,
                                loggingMeta,
                                env.apprecQueueName,
                                journalService,
                                receivedDialogmelding,
                                validationResult,
                                vedleggListe,
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
                                vedleggListe,
                                database,
                                pasientNavn,
                                navnSignerendeLege,
                                sha256String,
                            )
                        }

                        val currentRequestLatency = requestLatency.observeDuration()

                        log.info(
                            "Finished message got outcome {}, {}, processing took {}s",
                            StructuredArguments.keyValue("status", validationResult.status),
                            StructuredArguments.keyValue(
                                "ruleHits",
                                validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }),
                            StructuredArguments.keyValue("latency", currentRequestLatency),
                            StructuredArguments.fields(loggingMeta)
                        )
                    }
                } catch (e: Exception) {
                    MESSAGES_SENT_TO_BOQ.inc()
                    log.error("Exception caught while handling message, sending to backout, {}", e)
                    backoutProducer.send(message)
                }
            }
        }
    }

}

fun XMLDocument.toVedlegg(): Vedlegg {

    val base64Container = refDoc.content.any[0] as Base64Container

    return Vedlegg(
        mimeType = refDoc.mimeType,
        beskrivelse = refDoc.description,
        contentBase64 = base64Container.value
    )
}
