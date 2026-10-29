package no.nav.syfo.client.helsemelding

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.util.bearerHeader

/**
 * Klient for Team Helsemeldings attachment-service, som lagrer vedlegg til dialogmeldinger mottatt via helsemelding.dialog.in.
 * Se https://github.com/navikt/helsemelding-docs/blob/main/extern/attachment-service.md
 */
class AttachmentServiceClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val attachmentServiceClientId: String,
    private val attachmentServiceUrl: String,
    private val httpClient: HttpClient,
) {
    suspend fun hentVedlegg(messageId: String): List<AttachmentDTO> {
        val token = azureAdV2Client.getSystemToken(attachmentServiceClientId)?.accessToken
            ?: throw RuntimeException("Failed to get system token for attachment-service")

        return httpClient.get("$attachmentServiceUrl$ATTACHMENTS_PATH/$messageId") {
            header(HttpHeaders.Authorization, bearerHeader(token))
            accept(ContentType.Application.Json)
        }.body()
    }

    companion object {
        const val ATTACHMENTS_PATH = "/attachments"
    }
}

data class AttachmentDTO(
    val description: String,
    val contentType: String,
    val contentBase64: String,
)
