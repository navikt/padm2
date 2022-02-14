package no.nav.syfo.kafka

import java.util.Properties
import no.nav.syfo.ApplicationEnvironmentKafka
import no.nav.syfo.application.kafka.JacksonKafkaSerializer
import no.nav.syfo.application.kafka.commonKafkaAivenProducerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer

fun kafkaDialogmeldingProducerConfig(
    kafkaEnvironment: ApplicationEnvironmentKafka,
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenProducerConfig(kafkaEnvironment))
        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonKafkaSerializer::class.java.canonicalName
    }
}
