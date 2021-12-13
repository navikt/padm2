package no.nav.syfo.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.util.retry
import no.nav.syfo.logger
import java.io.IOException

class LegeSuspensjonClient(
    private val endpointUrl: String,
    private val username: String,
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient
) {

    suspend fun checkTherapist(therapistId: String, ediloggid: String, oppslagsdato: String): Suspendert = retry(callName = "check_therapist") {
        val httpResponse: HttpResponse = httpClient.get("$endpointUrl/api/v1/suspensjon/status") {
            accept(ContentType.Application.Json)
            val oidcToken = stsClient.oidcToken()
            headers {
                append("Nav-Call-Id", ediloggid)
                append("Nav-Consumer-Id", username)
                append("Nav-Personident", therapistId)
                append("Authorization", "Bearer ${oidcToken.access_token}")
            }
            parameter("oppslagsdato", oppslagsdato)
        }

        if (httpResponse.status != HttpStatusCode.OK) {
            logger.error("Btsys svarte med kode {} for ediloggId {}, {}", httpResponse.status, ediloggid)
            throw IOException("Btsys svarte med uventet kode ${httpResponse.status} for $ediloggid")
        }

        httpResponse.call.response.receive()
    }
}

data class Suspendert(val suspendert: Boolean)
