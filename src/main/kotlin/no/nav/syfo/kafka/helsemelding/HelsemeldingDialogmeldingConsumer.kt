package no.nav.syfo.kafka.helsemelding

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.DialogmeldingProcessor
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.metrics.HELSEMELDING_DUPLICATE_MESSAGE_COUNTER
import no.nav.syfo.metrics.HELSEMELDING_INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.HELSEMELDING_INVALID_MESSAGE_COUNTER
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.persistering.db.lagreMottattDialogmeldingFraHelsemelding
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.util.UUID

class HelsemeldingDialogmeldingConsumer(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val applicationState: ApplicationState,
    private val database: DatabaseInterface,
    private val dialogmeldingProcessor: DialogmeldingProcessor,
) {
    suspend fun run() {
        kafkaConsumer.use { consumer ->
            consumer.subscribe(listOf(HELSEMELDING_DIALOGMELDING_TOPIC))
            logger.info("Started consuming topic $HELSEMELDING_DIALOGMELDING_TOPIC")
            while (applicationState.ready) {
                pollAndProcess()
            }
        }
    }

    suspend fun pollAndProcess() {
        val records = kafkaConsumer.poll(POLL_DURATION)
        if (records.isEmpty) return

        // Lagrer hele batchen før commit; feil ved lagring kastes videre slik at meldingene konsumeres på nytt.
        val nyeMeldinger = records.mapNotNull { lagreHvisNy(it) }
        kafkaConsumer.commitSync()

        nyeMeldinger.forEach { receivedDialogmelding ->
            try {
                dialogmeldingProcessor.process(receivedDialogmelding)
            } catch (e: Exception) {
                logger.warn(
                    "Exception caught while processing message from Helsemelding, will try again later: ${e.message}, {}",
                    StructuredArguments.fields(receivedDialogmelding.loggingMeta()),
                    e,
                )
            }
        }
    }

    private fun lagreHvisNy(record: ConsumerRecord<String, String>): ReceivedDialogmelding? {
        HELSEMELDING_INCOMING_MESSAGE_COUNTER.increment()
        val receivedDialogmelding = try {
            receivedDialogmeldingFromHelsemelding(
                dialogmeldingId = UUID.randomUUID().toString(),
                kafkaMelding = requireNotNull(record.value()) { "Record value is null" },
            )
        } catch (e: Exception) {
            HELSEMELDING_INVALID_MESSAGE_COUNTER.increment()
            logger.error(
                "Skipping invalid message from $HELSEMELDING_DIALOGMELDING_TOPIC: " +
                    "partition=${record.partition()}, offset=${record.offset()}, " +
                    "exception=${e::class.qualifiedName}"
            )
            return null
        }

        val loggingMeta = receivedDialogmelding.loggingMeta()
        return if (database.lagreMottattDialogmeldingFraHelsemelding(receivedDialogmelding)) {
            logger.info("Received and stored message from Helsemelding, {}", StructuredArguments.fields(loggingMeta))
            receivedDialogmelding
        } else {
            HELSEMELDING_DUPLICATE_MESSAGE_COUNTER.increment()
            logger.warn("Skipping duplicate message from Helsemelding, {}", StructuredArguments.fields(loggingMeta))
            null
        }
    }

    companion object {
        private val POLL_DURATION = Duration.ofSeconds(1)
    }
}
