package no.nav.syfo

import io.ktor.server.netty.*
import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.mock.AzureAdV2Mock

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()
    val embeddedEnvironment: KafkaEnvironment = testKafka()

    val azureAdV2Mock = AzureAdV2Mock()

    val externalApplicationMockMap = hashMapOf(
        azureAdV2Mock.name to azureAdV2Mock.server,
    )

    var environment = testEnvironment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        azureTokenEndpoint = azureAdV2Mock.url,
    )

    companion object {
        private val instance: ExternalMockEnvironment by lazy {
            ExternalMockEnvironment().also {
                it.startExternalMocks()
            }
        }
    }
}

fun ExternalMockEnvironment.startExternalMocks() {
    this.externalApplicationMockMap.start()
    this.embeddedEnvironment.start()
}

fun ExternalMockEnvironment.stopExternalMocks() {
    this.externalApplicationMockMap.stop()
    this.database.stop()
    this.embeddedEnvironment.tearDown()
}

fun HashMap<String, NettyApplicationEngine>.start() {
    this.forEach {
        it.value.start()
    }
}

fun HashMap<String, NettyApplicationEngine>.stop(
    gracePeriodMillis: Long = 1L,
    timeoutMillis: Long = 10L,
) {
    this.forEach {
        it.value.stop(gracePeriodMillis, timeoutMillis)
    }
}
