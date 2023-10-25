package no.nav.syfo.client.isbehandlerdialog

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.httpClient
import no.nav.syfo.util.bearerHeader

class BehandlerdialogClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val behandlerdialogClientId: String,
    behandlerdialogUrl: String,
) {

    private val meldingUrl = "$behandlerdialogUrl/$MELDINGER_PATH"

    suspend fun isMeldingInModia(msgId: String): Boolean {
        val systemToken = azureAdV2Client.getSystemToken(
            scopeClientId = behandlerdialogClientId,
        )?.accessToken ?: throw RuntimeException("Failed to get system token")

        return try {
            val response = httpClient.get("$meldingUrl/$msgId") {
                header(HttpHeaders.Authorization, bearerHeader(systemToken))
                accept(ContentType.Application.Json)
            }
            response.status == HttpStatusCode.OK
        } catch (e: ResponseException) {
            throw RuntimeException("Could not fetch melding from isbehandlerdialog for msgId=$msgId", e)
        }
    }

    companion object {
        const val MELDINGER_PATH = "/api/system/v1/meldinger"
    }
}
