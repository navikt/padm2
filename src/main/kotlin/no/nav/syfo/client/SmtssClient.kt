package no.nav.syfo.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.logger
import no.nav.syfo.util.bearerHeader

class SmtssClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val smtssClientId: String,
    private val smtssUrl: String,
    private val httpClient: HttpClient
) {
    suspend fun findBestTssIdEmottak(
        legePersonIdent: PersonIdent,
        legekontorOrgName: String,
        dialogmeldingId: String,
    ): String? {
        val emottakPath = "/api/v1/samhandler/emottak"
        return findBestTss(
            path = emottakPath,
            legePersonIdent = legePersonIdent,
            legekontorOrgName = legekontorOrgName,
            dialogmeldingId = dialogmeldingId,
        )
    }

    suspend fun findBestTssIdInfotrygd(
        legePersonIdent: PersonIdent,
        legekontorOrgName: String,
        dialogmeldingId: String,
    ): String? {
        val infotrygdPath = "/api/v1/samhandler/infotrygd"
        return findBestTss(
            path = infotrygdPath,
            legePersonIdent = legePersonIdent,
            legekontorOrgName = legekontorOrgName,
            dialogmeldingId = dialogmeldingId,
        )
    }

    private suspend fun findBestTss(
        path: String,
        legePersonIdent: PersonIdent,
        legekontorOrgName: String,
        dialogmeldingId: String,
    ): String? {
        val token = azureAdV2Client.getSystemToken(smtssClientId)
            ?: throw RuntimeException("TSS TRACE: Failed to send request to smtss: No token was found")

        return try {
            val response = httpClient.get("$smtssUrl$path") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header("samhandlerFnr", legePersonIdent.value)
                header("samhandlerOrgName", legekontorOrgName)
                header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
                header("requestId", dialogmeldingId)
            }
            response.body<TSSident>().tssid
        } catch (exception: ResponseException) {
            when (exception.response.status) {
                HttpStatusCode.NotFound -> {
                    logger.info("TSS TRACE: Fant ikke tssId i smtss, fikk 404")
                    null
                }
                else -> {
                    logger.info("TSS TRACE: Fant ikke tssId i smtss, noe feilet med status: ${exception.response.status}")
                    null
                }
            }
        }
    }
}

data class TSSident(
    val tssid: String,
)
