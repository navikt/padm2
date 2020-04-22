package no.nav.syfo.application

import io.ktor.util.KtorExperimentalAPI
import java.io.StringReader
import javax.jms.MessageConsumer
import javax.jms.Session
import javax.jms.TextMessage
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.application.services.samhandlerParksisisLegevakt
import no.nav.syfo.application.services.startSubscription
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.model.findDialogmeldingType
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractDialogmelding
import no.nav.syfo.util.extractOrganisationHerNumberFromSender
import no.nav.syfo.util.extractOrganisationNumberFromSender
import no.nav.syfo.util.extractSenderOrganisationName
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.wrapExceptions

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
        subscriptionEmottak: SubscriptionPort
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

                    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
                    val msgHead: XMLMsgHead = fellesformat.get()
                    val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
                    val personNumberPatient = msgHead.msgInfo.patient.ident.find { it.typeId.v == "FNR" }?.id ?: "" // TODO dersom tom avvist meldingen
                    val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur
                    val legekontorOrgName = extractSenderOrganisationName(fellesformat)
                    val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
                    val dialogmelding = extractDialogmelding(fellesformat)
                    val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

                    val loggingMeta = LoggingMeta(
                            mottakId = receiverBlock.ediLoggId,
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
                } catch (e: Exception) {
                    log.error("Exception caught while handling message {}", e)
                }
            }
        }
    }
}
