package no.nav.syfo.mock

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import no.nav.syfo.Environment
import no.nav.syfo.util.configure

fun mockHttpClient(
    environment: Environment,
    pdfGenMockState: PdfGenMockState,
    pdlMockState: PdlMockState,
) = HttpClient(MockEngine) {
    install(ContentNegotiation) {
        jackson { configure() }
    }
    expectSuccess = true
    engine {
        addHandler { request ->
            val requestUrl = request.url.toString()
            when {
                requestUrl.startsWith(environment.aadTokenEndpoint) -> azureAdV2MockResponse()
                requestUrl.startsWith(environment.pdlUrl) -> pdlMockResponse(request, pdlMockState)
                requestUrl.startsWith(environment.clamavURL) -> clamAvMockResponse(request)
                requestUrl.startsWith(environment.dokArkivUrl) -> dokarkivMockResponse(request)
                requestUrl.startsWith(environment.syfopdfgen) -> pdfGenMockResponse(request, pdfGenMockState)
                requestUrl.startsWith(environment.syfohelsenettproxyEndpointURL) -> syfohelsenettproxyMockResponse(request)
                requestUrl.startsWith(environment.legeSuspensjonEndpointURL) -> legeSuspensjonMockResponse()
                requestUrl.startsWith(environment.smtssApiUrl) -> smtssMockResponse()
                requestUrl.startsWith(environment.smgcpProxyUrl) -> smgcpMockResponse()
                requestUrl.startsWith(environment.isbehandlerdialogUrl) -> behandlerdialogMockResponse(request)
                else -> error("Unhandled ${request.url}")
            }
        }
    }
}
