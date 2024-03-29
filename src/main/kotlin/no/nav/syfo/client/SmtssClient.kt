package no.nav.syfo.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.logger
import no.nav.syfo.util.bearerHeader
import java.io.IOException

class SmtssClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val smtssClientId: String,
    private val smtssUrl: String,
    private val httpClient: HttpClient
) {
    val path = "/api/v1/samhandler/emottak"

    suspend fun findBestTss(
        legePersonIdent: PersonIdent,
        legekontorOrgName: String,
        dialogmeldingId: String,
    ): TssId? {
        val token = azureAdV2Client.getSystemToken(smtssClientId)
            ?: throw RuntimeException("Failed to send request to smtss: No token was found")

        return try {
            val dashes = "[–—]"
            val hyphen = "-"
            val kontorOrgNameNoIllegalChars = legekontorOrgName.replace(regex = Regex(dashes), hyphen)
            val response = httpClient.get("$smtssUrl$path") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header("samhandlerFnr", legePersonIdent.value)
                header("samhandlerOrgName", kontorOrgNameNoIllegalChars)
                header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
                header("requestId", dialogmeldingId)
            }
            val responseBody = response.body<TssId>()
            logger.info("Fant tssId: ${responseBody.tssid}, requestId: $dialogmeldingId")
            responseBody
        } catch (exception: ResponseException) {
            when (exception.response.status) {
                HttpStatusCode.NotFound -> {
                    logger.info("Fant ikke tssId i smtss, fikk 404, requestId: $dialogmeldingId")
                    null
                }
                else -> {
                    logger.error("Fant ikke tssId i smtss, noe feilet med status: ${exception.response.status}, requestId: $dialogmeldingId")
                    throw IOException("Vi fikk en uventet feil fra smtss, prøver på nytt! ${exception.response.bodyAsChannel()}, requestId: $dialogmeldingId")
                }
            }
        }
    }
}

data class TssId(
    val tssid: String,
)
