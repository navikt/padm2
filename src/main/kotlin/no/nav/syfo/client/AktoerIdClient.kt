package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class AktoerIdClient(
    private val endpointUrl: String,
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun getAktoerIds(
        personNumbers: List<String>,
        username: String,
        loggingMeta: LoggingMeta
    ): Map<String, IdentInfoResult> =
        retry("get_aktoerids") {
            httpClient.get<Map<String, IdentInfoResult>>("$endpointUrl/identer") {
                accept(ContentType.Application.Json)
                val oidcToken = stsClient.oidcToken()
                headers {
                    append("Authorization", "Bearer ${oidcToken.access_token}")
                    append("Nav-Consumer-Id", username)
                    append("Nav-Call-Id", loggingMeta.msgId)
                    append("Nav-Personidenter", personNumbers.joinToString(","))
                }
                parameter("gjeldende", "true")
                parameter("identgruppe", "AktoerId")
            }
        }
}

data class IdentInfo(
    val ident: String,
    val identgruppe: String,
    val gjeldende: Boolean
)

data class IdentInfoResult(
    val identer: List<IdentInfo>?,
    val feilmelding: String?
)
