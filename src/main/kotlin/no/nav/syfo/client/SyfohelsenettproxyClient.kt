package no.nav.syfo.client


import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.logger
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.retry
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
    ): HelsenettProxyBehandler? = retry("finn_behandler") {
        logger.info("Henter behandler fra syfohelsenettproxy for msgId {}", msgId)

        val accessToken = azureAdV2Client.getSystemToken(helsenettClientId)?.accessToken
            ?: run {
                logger.error("Syfohelsenettproxy kunne ikke hente AzureAdV2 token for msgID {}, {}", msgId, fields(loggingMeta))
                return@retry null
            }

        val response: HttpResponse = httpClient.get("$endpointUrl/api/v2/behandler") {
            accept(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
                append("Nav-CallId", msgId)
                append("behandlerFnr", behandlerFnr)
            }
        }

        when (response.status) {
            InternalServerError -> {
                logger.error("Syfohelsenettproxy svarte med feilmelding for msgId {}, {}", msgId, fields(loggingMeta))
                throw IOException("Syfohelsenettproxy svarte med feilmelding for $msgId")
            }

            BadRequest -> {
                logger.error("BehandlerFnr mangler i request for msgId {}, {}", msgId, fields(loggingMeta))
                null
            }

            NotFound -> {
                logger.warn("BehandlerFnr ikke funnet {}, {}", msgId, fields(loggingMeta))
                null
            }
            else -> {
                logger.info("Hentet behandler for msgId {}, {}", msgId, fields(loggingMeta))
                response.receive<HelsenettProxyBehandler>()
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
