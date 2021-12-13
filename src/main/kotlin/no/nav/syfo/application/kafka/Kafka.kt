package no.nav.syfo.application.kafka

import no.nav.syfo.ApplicationEnvironmentKafka
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import java.util.Properties

fun commonKafkaAivenProducerConfig(kafkaEnvironment: ApplicationEnvironmentKafka) = Properties().apply {
    this[SaslConfigs.SASL_MECHANISM] = "PLAIN"
    this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = kafkaEnvironment.bootstrapServers
    this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SSL"
    this[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = ""
    this[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "jks"
    this[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
    this[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = kafkaEnvironment.aivenTruststoreLocation
    this[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = kafkaEnvironment.aivenCredstorePassword
    this[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = kafkaEnvironment.aivenKeystoreLocation
    this[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = kafkaEnvironment.aivenCredstorePassword
    this[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = kafkaEnvironment.aivenCredstorePassword
    this[ProducerConfig.ACKS_CONFIG] = "all"
    this[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = "true"
    this[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = "1"
    this[ProducerConfig.MAX_BLOCK_MS_CONFIG] = "15000"
    this[ProducerConfig.RETRIES_CONFIG] = "100000"
}
