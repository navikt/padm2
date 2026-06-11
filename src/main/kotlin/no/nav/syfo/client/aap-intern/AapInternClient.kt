package no.nav.syfo.client.`aap-intern`

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.util.bearerHeader
import org.slf4j.LoggerFactory

class AapInternClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val aapInternClientId: String,
    aapInternUrl: String,
    private val httpClient: HttpClient,
) {

    private val meldingUrl = "$aapInternUrl/$MELDINGER_PATH"

    suspend fun isMeldingInKelvin(msgId: String): Boolean {
        val systemToken = azureAdV2Client.getSystemToken(
            scopeClientId = aapInternClientId,
        )?.accessToken ?: throw RuntimeException("Failed to get system token")

        return try {
            val response = httpClient.get("$meldingUrl/$msgId/eksisterer") {
                header(HttpHeaders.Authorization, bearerHeader(systemToken))
                accept(ContentType.Application.Json)
            }
            (response.status == HttpStatusCode.OK && response.body<AAPResponse>().eksisterer).also {
                log.info("Dialogmelding $msgId finnes i Kelvin: $it")
            }
        } catch (e: ResponseException) {
            throw RuntimeException("Could not fetch melding from isbehandlerdialog for msgId=$msgId", e)
        }
    }

    companion object {
        const val MELDINGER_PATH = "syfo/v1/dialogmelding"
        private val log = LoggerFactory.getLogger(AapInternClient::class.java)
    }
}

data class AAPResponse(
    val eksisterer: Boolean,
)
