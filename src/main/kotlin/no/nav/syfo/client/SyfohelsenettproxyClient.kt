package no.nav.syfo.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.logger
import no.nav.syfo.util.LoggingMeta
import java.io.IOException

class SyfohelsenettproxyClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val endpointUrl: String,
    private val httpClient: HttpClient,
    private val helsenettClientId: String
) {

    suspend fun finnBehandler(
        behandlerFnr: String,
        msgId: String,
        loggingMeta: LoggingMeta
    ): HelsenettProxyBehandler? {
        logger.info("Henter behandler fra syfohelsenettproxy for msgId {}", msgId)

        val accessToken = azureAdV2Client.getSystemToken(helsenettClientId)?.accessToken
            ?: throw RuntimeException("Failed to send request to SyfohelsenettProxy: No token was found")

        return try {
            val response: HttpResponse = httpClient.get("$endpointUrl/api/v2/behandler") {
                accept(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                    append("Nav-CallId", msgId)
                    append("behandlerFnr", behandlerFnr)
                }
            }
            response.body<HelsenettProxyBehandler>()
        } catch (exception: ResponseException) {
            when (exception.response.status) {
                BadRequest -> {
                    logger.error("BehandlerFnr mangler i request for msgId {}, {}", msgId, fields(loggingMeta))
                    null
                }
                NotFound -> {
                    logger.warn("BehandlerFnr ikke funnet {}, {}", msgId, fields(loggingMeta))
                    null
                }
                else -> {
                    logger.error("Syfohelsenettproxy svarte med feilmelding for msgId {}, {}", msgId, fields(loggingMeta))
                    throw IOException("Syfohelsenettproxy svarte med feilmelding for $msgId")
                }
            }
        }
    }
}

data class HelsenettProxyBehandler(
    val godkjenninger: List<Godkjenning>,
    val fnr: String?,
    val hprNummer: Int?,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?
)

data class Godkjenning(
    val helsepersonellkategori: Kode? = null,
    val autorisasjon: Kode? = null
)

data class Kode(
    val aktiv: Boolean,
    val oid: Int,
    val verdi: String?
)
