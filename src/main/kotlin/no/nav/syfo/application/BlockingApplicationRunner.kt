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
import no.nav.syfo.application.services.samhandlerParksisisLegevakt
import no.nav.syfo.application.services.startSubscription
import no.nav.syfo.client.*
import no.nav.syfo.db.Database
import no.nav.syfo.handlestatus.*
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.MESSAGES_SENT_TO_BOQ
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.*
import no.nav.syfo.services.BehandlerService
import no.nav.syfo.services.JournalService
import no.nav.syfo.services.sha256hashstring
import no.nav.syfo.services.updateRedis
import no.nav.syfo.util.*
import no.nav.syfo.validation.validateDialogMeldingKodeverk
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisConnectionException
import java.io.StringReader
import java.time.ZoneOffset
import java.util.*
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage

class BlockingApplicationRunner {

    val APPROVED_DOCTORS = listOf(
        "7030843", // Vår første lege i prod
        "7070896", // Prodlege
        "9682414", // Prodlege
        "7617046", // Prodlege
        "1234567", // Testlege i preprod
        "9999973" // Testlege i EPIC
    )

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
        jedis: Jedis,
        receiptProducer: MessageProducer,
        padm2ReglerClient: Padm2ReglerClient,
        backoutProducer: MessageProducer,
        journalService: JournalService,
        arenaProducer: MessageProducer,
        database: Database,
        eiaProducer: MessageProducer,
        behandlerService: BehandlerService
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
                    val legekontorReshId = extractOrganisationRashNumberFromSender(fellesformat)?.id
                    val dialogmeldingXml = extractDialogmelding(fellesformat)
                    val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)
                    val sha256String = sha256hashstring(dialogmeldingXml)
                    val legeHpr = extractLegeHpr(fellesformat)

                    val approvedDoctor = isApprovedDoctor(legeHpr)

                    if (!approvedDoctor) {
                        eiaProducer.send(message)
                        log.info("Proxying message to Eia")
                        continue@loop
                    }

                    val navnHelsePersonellNavn = extractHelsePersonellNavn(fellesformat)
                    val extractVedlegg = extractVedlegg(fellesformat)
                    val pasientNavn = extractPasientNavn(fellesformat)
                    val vedleggListe = extractVedlegg.map { it.toVedlegg() }

                    val requestLatency = REQUEST_TIME.startTimer()

                    val loggingMeta = LoggingMeta(
                        mottakId = ediLoggId,
                        orgNr = extractOrganisationNumberFromSender(fellesformat)?.id,
                        msgId = msgHead.msgInfo.msgId
                    )

                    log.info("Received message, {}", StructuredArguments.fields(loggingMeta))

                    val navnSignerendeLege = behandlerService.behandlernavn(personNumberDoctor, msgId, loggingMeta)

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
                                null -> log.info(
                                    "SamhandlerPraksis is Not found, {}",
                                    StructuredArguments.fields(loggingMeta)
                                )
                                else -> if (!samhandlerParksisisLegevakt(samhandlerPraksis) &&
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

                        val redisSha256String = jedis.get(sha256String)
                        val redisEdiloggid = jedis.get(ediLoggId)

                        if (redisSha256String != null) {
                            handleDuplicateSM2013Content(
                                session, receiptProducer,
                                fellesformat, loggingMeta, env, redisSha256String
                            )
                            continue@loop
                        } else if (redisEdiloggid != null) {
                            handleDuplicateEdiloggid(
                                session, receiptProducer,
                                fellesformat, loggingMeta, env, redisEdiloggid
                            )
                            continue@loop
                        } else {
                            updateRedis(jedis, ediLoggId, sha256String)
                        }

                        val patientIdents = aktoerIds[personNumberPatient]
                        val doctorIdents = aktoerIds[personNumberDoctor]

                        if (patientIdents == null || patientIdents.feilmelding != null) {
                            handlePatientNotFoundInAktorRegister(
                                patientIdents, session,
                                receiptProducer, fellesformat, ediLoggId, jedis, sha256String, env, loggingMeta
                            )
                            continue@loop
                        }
                        if (doctorIdents == null || doctorIdents.feilmelding != null) {
                            handleDoctorNotFoundInAktorRegister(
                                doctorIdents, session,
                                receiptProducer, fellesformat, ediLoggId, jedis, sha256String, env, loggingMeta
                            )
                            continue@loop
                        }
                        if (erTestFnr(personNumberPatient) && env.cluster == "prod-fss") {
                            handleTestFnrInProd(
                                session, receiptProducer, fellesformat,
                                ediLoggId, jedis, sha256String, env, loggingMeta
                            )
                            continue@loop
                        }
                        if ((dialogmeldingType == DialogmeldingType.DIALOGMELDING_HENVENDELSE_FRA_LEGE_HENDVENDELSE ||
                                    dialogmeldingType == DialogmeldingType.DIALOGMELDING_FORESPORSEL_FRA_SAKSBEHANDLER_FORESPORSEL_SVAR) &&
                            dialogmeldingXml.notat.first().tekstNotatInnhold.isNullOrEmpty()
                        ) {
                            handleMeldingsTekstMangler(
                                session, receiptProducer, fellesformat,
                                ediLoggId, jedis, sha256String, env, loggingMeta
                            )
                            continue@loop
                        }

                        if (!validateDialogMeldingKodeverk(dialogmeldingXml, dialogmeldingType)) {
                            handleInvalidDialogMeldingKodeverk(
                                session, receiptProducer, fellesformat,
                                ediLoggId, jedis, sha256String, env, loggingMeta
                            )
                            continue@loop
                        }

                        val dialogmelding = dialogmeldingXml.toDialogmelding(
                            dialogmeldingId = UUID.randomUUID().toString(),
                            dialogmeldingType = dialogmeldingType,
                            signaturDato = msgHead.msgInfo.genDate,
                            navnHelsePersonellNavn = navnHelsePersonellNavn
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

                        val validationResult = padm2ReglerClient.executeRuleValidation(receivedDialogmelding)

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
                                navnSignerendeLege
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
                                navnSignerendeLege
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
                } catch (jedisException: JedisConnectionException) {
                    log.error(
                        "Exception caught, redis issue while handling message, sending to backout",
                        jedisException
                    )
                    backoutProducer.send(message)
                    MESSAGES_SENT_TO_BOQ.inc()
                    log.error("Setting applicationState.alive to false")
                    applicationState.alive = false
                } catch (e: Exception) {
                    MESSAGES_SENT_TO_BOQ.inc()
                    log.error("Exception caught while handling message, sending to backout, {}", e)
                    backoutProducer.send(message)
                }
            }
        }
    }

    fun isApprovedDoctor(legeHpr: String?): Boolean {
        return APPROVED_DOCTORS.contains(legeHpr)
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
