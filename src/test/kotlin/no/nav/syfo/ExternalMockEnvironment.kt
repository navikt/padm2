package no.nav.syfo

import io.ktor.server.netty.*
import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.mock.*

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()
    val embeddedEnvironment: KafkaEnvironment = testKafka()

    val azureAdV2Mock = AzureAdV2Mock()
    val dokarkivMock = DokarkivMock()
    val aktoerIdMock = AktoerIdMock()
    val kuhrsarMock = KuhrSarMock()
    val pdfgenMock = PdfGenMock()
    val syfohelsenettproxyMock = SyfohelsenettproxyMock()
    val legeSuspensjonEndpointMock = LegeSuspensjonEndpointMock()
    val stsMock = StsMock()

    val externalApplicationMockMap = hashMapOf(
        azureAdV2Mock.name to azureAdV2Mock.server,
        dokarkivMock.name to dokarkivMock.server,
        aktoerIdMock.name to aktoerIdMock.server,
        kuhrsarMock.name to kuhrsarMock.server,
        pdfgenMock.name to pdfgenMock.server,
        syfohelsenettproxyMock.name to syfohelsenettproxyMock.server,
        stsMock.name to stsMock.server,
        legeSuspensjonEndpointMock.name to legeSuspensjonEndpointMock.server,
    )

    var environment = testEnvironment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        azureTokenEndpoint = azureAdV2Mock.url,
        dokarkivUrl = dokarkivMock.url,
        aktoerIdUrl = aktoerIdMock.url,
        kuhrSarApiUrl = kuhrsarMock.url,
        pdfgenUrl = pdfgenMock.url,
        syfohelsenettproxyEndpointUrl = syfohelsenettproxyMock.url,
        legeSuspensjonEndpointUrl = legeSuspensjonEndpointMock.url,
        stsClientUrl = stsMock.url,
    )

    companion object {
        val instance: ExternalMockEnvironment by lazy {
            ExternalMockEnvironment().also {
                it.startExternalMocks()
            }
        }
    }
}

fun ExternalMockEnvironment.startExternalMocks() {
    this.externalApplicationMockMap.start()
    this.database.start()
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
