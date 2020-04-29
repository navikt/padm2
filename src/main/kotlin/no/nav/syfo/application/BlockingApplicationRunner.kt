package no.nav.syfo.application

import io.ktor.util.KtorExperimentalAPI
import java.io.StringReader
import java.time.ZoneOffset
import java.util.UUID
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage
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
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.Padm2ReglerClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.handlestatus.handleDoctorNotFoundInAktorRegister
import no.nav.syfo.handlestatus.handleDuplicateEdiloggid
import no.nav.syfo.handlestatus.handleDuplicateSM2013Content
import no.nav.syfo.handlestatus.handlePatientNotFoundInAktorRegister
import no.nav.syfo.handlestatus.handleStatusINVALID
import no.nav.syfo.handlestatus.handleStatusOK
import no.nav.syfo.handlestatus.handleTestFnrInProd
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.model.findDialogmeldingType
import no.nav.syfo.model.toDialogmelding
import no.nav.syfo.services.JournalService
import no.nav.syfo.services.sha256hashstring
import no.nav.syfo.services.updateRedis
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.erTestFnr
import no.nav.syfo.util.extractDialogmelding
import no.nav.syfo.util.extractHelsePersonellNavn
import no.nav.syfo.util.extractLegeHpr
import no.nav.syfo.util.extractOrganisationHerNumberFromSender
import no.nav.syfo.util.extractOrganisationNumberFromSender
import no.nav.syfo.util.extractOrganisationRashNumberFromSender
import no.nav.syfo.util.extractSenderOrganisationName
import no.nav.syfo.util.extractVedlegg
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.wrapExceptions
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisConnectionException

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
        jedis: Jedis,
        receiptProducer: MessageProducer,
        padm2ReglerClient: Padm2ReglerClient,
        backoutProducer: MessageProducer,
        journalService: JournalService,
        arenaProducer: MessageProducer
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
                    val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
                    val personNumberPatient = msgHead.msgInfo.patient.ident.find { it.typeId.v == "FNR" }?.id
                        ?: "" // TODO dersom tom avvist meldingen
                    val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur
                    val legekontorOrgName = extractSenderOrganisationName(fellesformat)
                    val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
                    val legekontorReshId = extractOrganisationRashNumberFromSender(fellesformat)?.id
                    val dialogmeldingXml = extractDialogmelding(fellesformat)
                    val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)
                    val sha256String = sha256hashstring(dialogmeldingXml)
                    val legeHpr = extractLegeHpr(fellesformat)
                    val navnHelsePersonellNavn = extractHelsePersonellNavn(fellesformat)
                    val extractVedlegg = extractVedlegg(fellesformat)
                    val vedleggListe = extractVedlegg.map { it.toVedlegg() }

                    val requestLatency = REQUEST_TIME.startTimer()

                    val loggingMeta = LoggingMeta(
                        mottakId = ediLoggId,
                        orgNr = extractOrganisationNumberFromSender(fellesformat)?.id,
                        msgId = msgHead.msgInfo.msgId
                    )

                    log.info("Received message, {}", StructuredArguments.fields(loggingMeta))

                    INCOMING_MESSAGE_COUNTER.inc()

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
                            receiptProducer, fellesformat, ediLoggId, jedis, redisSha256String, env, loggingMeta
                        )
                        continue@loop
                    }
                    if (doctorIdents == null || doctorIdents.feilmelding != null) {
                        handleDoctorNotFoundInAktorRegister(
                            doctorIdents, session,
                            receiptProducer, fellesformat, ediLoggId, jedis, redisSha256String, env, loggingMeta
                        )
                        continue@loop
                    }
                    if (erTestFnr(personNumberPatient) && env.cluster == "prod-fss") {
                        handleTestFnrInProd(
                            session, receiptProducer, fellesformat,
                            ediLoggId, jedis, redisSha256String, env, loggingMeta
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
                            receiverBlock
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
                            vedleggListe
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
                } catch (jedisException: JedisConnectionException) {
                    log.error(
                        "Exception caught, redis issue while handling message, sending to backout",
                        jedisException
                    )
                    backoutProducer.send(message)
                    log.error("Setting applicationState.alive to false")
                    applicationState.alive = false
                } catch (e: Exception) {
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
