package no.nav.syfo.application

import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.domain.elevenDigits
import no.nav.syfo.handlestatus.*
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.MESSAGES_SENT_TO_BOQ
import no.nav.syfo.persistering.persistReceivedMessage
import no.nav.syfo.util.*
import java.io.StringReader
import java.util.*
import javax.jms.*

class BlockingApplicationRunner(
    val applicationState: ApplicationState,
    val database: DatabaseInterface,
    val env: Environment,
    val inputconsumer: MessageConsumer,
    val mqSender: MQSenderInterface,
    val dialogmeldingProducer: DialogmeldingProducer,
) {
    val dialogmeldingProcessor = DialogmeldingProcessor(
        database = database,
        env = env,
        mqSender = mqSender,
        dialogmeldingProducer = dialogmeldingProducer,
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

    suspend fun processMessageHandleException(message: Message): String? {
        val inputMessageText = when (message) {
            is TextMessage -> message.text
            else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
        }
        val dialogmeldingId: String? = try {
            storeMessage(inputMessageText)
        } catch (e: Exception) {
            mqSender.sendBackout(message)
            MESSAGES_SENT_TO_BOQ.inc()
            logger.error("Exception caught while storing message, sent to backout: {}", e.message)
            null
        } catch (t: Throwable) {
            try {
                mqSender.sendBackout(message)
                MESSAGES_SENT_TO_BOQ.inc()
                logger.error("Error caught while storing message, sent to backout: {}", t.message)
                null
            } finally {
                throw t
            }
        }

        try {
            if (inputMessageText != null && dialogmeldingId != null) {
                dialogmeldingProcessor.processMessage(dialogmeldingId, inputMessageText)
            }
        } catch (e: Exception) {
            logger.warn("Exception caught while processing message, will try again later: ${e.message}", e)
        }
        return dialogmeldingId
    }

    private fun storeMessage(
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
        if (innbyggerIdent.isNullOrEmpty() || !elevenDigits.matches(innbyggerIdent)) {
            handlePatientMissing(
                mqSender,
                fellesformat,
                loggingMeta,
            )
            return null
        }

        val dialogmeldingId = UUID.randomUUID().toString()

        val receivedDialogmelding = dialogmeldingProcessor.createReceivedDialogmelding(
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
}
