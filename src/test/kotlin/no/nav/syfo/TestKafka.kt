package no.nav.syfo

import no.nav.common.KafkaEnvironment

import no.nav.syfo.kafka.DialogmeldingProducer.Companion.DIALOGMELDING_TOPIC

fun testKafka(
    autoStart: Boolean = false,
    withSchemaRegistry: Boolean = false,
    topicNames: List<String> = listOf(
        DIALOGMELDING_TOPIC,
    )
) = KafkaEnvironment(
    autoStart = autoStart,
    withSchemaRegistry = withSchemaRegistry,
    topicNames = topicNames,
)
