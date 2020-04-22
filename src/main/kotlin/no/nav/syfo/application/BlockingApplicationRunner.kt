package no.nav.syfo.application

import io.ktor.util.KtorExperimentalAPI
import java.io.StringReader
import javax.jms.MessageConsumer
import javax.jms.Session
import javax.jms.TextMessage
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.log
import no.nav.syfo.model.findDialogmeldingType
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractDialogmelding
import no.nav.syfo.util.extractOrganisationNumberFromSender
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
        secrets: VaultSecrets
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
                    val dialogmelding = extractDialogmelding(fellesformat)
                    val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

                    val loggingMeta = LoggingMeta(
                            mottakId = receiverBlock.ediLoggId,
                            orgNr = extractOrganisationNumberFromSender(fellesformat)?.id,
                            msgId = msgHead.msgInfo.msgId
                    )

                    log.info("Received message, {}", StructuredArguments.fields(loggingMeta))
                } catch (e: Exception) {
                    log.error("Exception caught while handling message {}", e)
                }
            }
        }
    }
}
