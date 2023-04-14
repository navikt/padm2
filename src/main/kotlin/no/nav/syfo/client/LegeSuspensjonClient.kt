package no.nav.syfo.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.logger
import java.io.IOException

class LegeSuspensjonClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val endpointUrl: String,
    private val endpointClientId: String,
    private val httpClient: HttpClient
) {

    suspend fun sjekkSuspensjon(
        behandlerId: String,
        ediloggid: String,
        oppslagsdato: String
    ): Suspendert {
        val token = azureAdV2Client.getSystemToken(endpointClientId)
            ?: throw RuntimeException("Failed to sjekk suspensjon: No token was found")

        val httpResponse: HttpResponse = httpClient.get("$endpointUrl/api/v1/btsys/suspensjon/status") {
            accept(ContentType.Application.Json)
            headers {
                append("Nav-Call-Id", ediloggid)
                append("Nav-Personident", behandlerId)
                append("Authorization", "Bearer ${token.accessToken}")
            }
            parameter("oppslagsdato", oppslagsdato)
        }

        if (httpResponse.status != HttpStatusCode.OK) {
            logger.error("Btsys (via isproxy) svarte med kode {} for ediloggId {}, {}", httpResponse.status, ediloggid)
            throw IOException("Btsys svarte (via isproxy) med uventet kode ${httpResponse.status} for $ediloggid")
        }

        return httpResponse.call.response.body()
    }
}

data class Suspendert(val suspendert: Boolean)
