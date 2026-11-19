package no.nav.syfo.client.sporhund

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

class SporhundClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val sporhundClientId: String,
    sporhundUrl: String,
    private val httpClient: HttpClient,
) {

    private val meldingUrl = "$sporhundUrl/$MELDINGER_PATH"

    suspend fun isMeldingInSpeil(msgId: String): Boolean {
        val systemToken = azureAdV2Client.getSystemToken(
            scopeClientId = sporhundClientId,
        )?.accessToken ?: throw RuntimeException("Failed to get system token")

        return try {
            val response = httpClient.get("$meldingUrl/$msgId/eksisterer") {
                header(HttpHeaders.Authorization, bearerHeader(systemToken))
                accept(ContentType.Application.Json)
            }
            (response.status == HttpStatusCode.OK && response.body<SporhundResponse>().eksisterer).also {
                log.info("Dialogmelding $msgId finnes i Speil: $it")
            }
        } catch (e: ResponseException) {
            throw RuntimeException("Could not fetch melding from sporhund for msgId=$msgId", e)
        }
    }

    companion object {
        const val MELDINGER_PATH = "api/syfo/v1/dialogmelding"
        private val log = LoggerFactory.getLogger(SporhundClient::class.java)
    }
}

data class SporhundResponse(
    val eksisterer: Boolean,
)
