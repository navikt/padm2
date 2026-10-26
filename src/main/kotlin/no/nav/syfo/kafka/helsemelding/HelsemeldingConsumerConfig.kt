package no.nav.syfo.kafka.helsemelding

import no.nav.syfo.ApplicationEnvironmentKafka
import no.nav.syfo.application.kafka.commonKafkaAivenConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.Properties

const val HELSEMELDING_DIALOGMELDING_TOPIC = "helsemelding.dialog.in"
private const val HELSEMELDING_CONSUMER_GROUP_ID = "padm2-helsemelding-dialog-in-v1"

// Verdien leses som String og parses i vår kode, slik at en ugyldig melding ikke får poll() til å kaste.
fun kafkaHelsemeldingConsumerConfig(
    kafkaEnvironment: ApplicationEnvironmentKafka,
): Properties = Properties().apply {
    putAll(commonKafkaAivenConsumerConfig(kafkaEnvironment))
    this[ConsumerConfig.GROUP_ID_CONFIG] = HELSEMELDING_CONSUMER_GROUP_ID
    this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
    this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
    this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "10"
}
