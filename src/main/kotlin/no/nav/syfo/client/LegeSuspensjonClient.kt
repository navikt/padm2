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
    private val applicationName: String,
    private val httpClient: HttpClient,
) {

    suspend fun sjekkSuspensjon(
        behandlerId: String,
        ediloggid: String,
        oppslagsdato: String
    ): Suspendert {
        val token = azureAdV2Client.getSystemToken(endpointClientId)
            ?: throw RuntimeException("Failed to sjekk suspensjon: No token was found")

        val httpResponse: HttpResponse = httpClient.post("$endpointUrl/api/v1/suspensjon/soek") {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            headers {
                append("Nav-Call-Id", ediloggid)
                append("Nav-Consumer-Id", applicationName)
                append("Authorization", "Bearer ${token.accessToken}")
            }
            setBody(SuspensjonSoekRequest(personident = behandlerId, oppslagsdato = oppslagsdato))
        }

        if (httpResponse.status != HttpStatusCode.OK) {
            logger.error("Btsys svarte med kode {} for ediloggId {}", httpResponse.status, ediloggid)
            throw IOException("Btsys svarte med uventet kode ${httpResponse.status} for $ediloggid")
        }

        return httpResponse.call.response.body()
    }
}

data class SuspensjonSoekRequest(val personident: String, val oppslagsdato: String)

data class Suspendert(val suspendert: Boolean)
