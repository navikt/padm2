package no.nav.syfo.client

import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.DialogmeldingSak
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer

class KafkaClients constructor(env: Environment, credentials: VaultSecrets) {

    private val kafkaBaseConfig = loadBaseConfig(env, credentials)
    private val producerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName,
        valueSerializer = JacksonKafkaSerializer::class)

    val kafkaProducerReceivedDialogmelding = KafkaProducer<String, ReceivedDialogmelding>(producerProperties)
    val kafkaProducerDialogmeldingSak = KafkaProducer<String, DialogmeldingSak>(producerProperties)
}
