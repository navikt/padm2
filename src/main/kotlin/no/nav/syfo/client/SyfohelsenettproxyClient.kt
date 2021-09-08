package no.nav.syfo.client

import no.nav.syfo.util.retry
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.util.*
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import java.io.IOException

@KtorExperimentalAPI
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
        log.info("Henter behandler fra syfohelsenettproxy for msgId {}", msgId)
        val httpStatement = httpClient.get<HttpStatement>("$endpointUrl/api/v2/behandler") {
            accept(ContentType.Application.Json)
            val accessToken = azureAdV2Client.getSystemToken(helsenettClientId)?.accessToken
            headers {
                append("Authorization", "Bearer $accessToken")
                append("Nav-CallId", msgId)
                append("behandlerFnr", behandlerFnr)
            }
        }

        val httpResponse = httpStatement.execute()

        when (httpResponse.status) {
            InternalServerError -> {
                log.error("Syfohelsenettproxy svarte med feilmelding for msgId {}, {}", msgId, fields(loggingMeta))
                throw IOException("Syfohelsenettproxy svarte med feilmelding for $msgId")
            }

            BadRequest -> {
                log.error("BehandlerFnr mangler i request for msgId {}, {}", msgId, fields(loggingMeta))
                null
            }

            NotFound -> {
                log.warn("BehandlerFnr ikke funnet {}, {}", msgId, fields(loggingMeta))
                null
            }
            else -> {
                log.info("Hentet behandler for msgId {}, {}", msgId, fields(loggingMeta))
                httpResponse.call.response.receive<HelsenettProxyBehandler>()
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
