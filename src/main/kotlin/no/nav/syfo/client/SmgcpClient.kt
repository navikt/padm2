package no.nav.syfo.client

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.msgHead.XMLSender
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.logger
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.getSenderMarshaller
import java.io.ByteArrayOutputStream
import java.io.IOException

class SmgcpClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val url: String,
    private val smgcpClientId: String,
    private val httpClient: HttpClient,
) {
    suspend fun startSubscription(
        tssId: TssId,
        sender: XMLSender,
        partnerReferanse: Int,
        msgId: String,
    ) {
        val token = azureAdV2Client.getSystemToken(smgcpClientId)
            ?: throw RuntimeException("Couldn't update emottak subscription in smgcp due to missing token")

        try {
            httpClient.post("$url/emottak/startsubscription") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
                header("Nav-Call-Id", msgId)
                setBody(
                    EmottakRequest(
                        tssIdent = tssId.tssid,
                        sender = convertSenderToBase64(sender),
                        partnerreferanse = partnerReferanse,
                    ),
                )
            }
            logger.info("Started subscription for tss: ${tssId.tssid} and partnerRef: $partnerReferanse, msgId: $msgId")
        } catch (exception: ResponseException) {
            logger.error("Couldn't update emottak subscription due to error: ${exception.response.status}, msgId: $msgId")
            throw IOException("Vi fikk en uventet feil fra smgcp, prøver på nytt! ${exception.response.bodyAsChannel()}, msgId: $msgId")
        }
    }

    private fun convertSenderToBase64(sender: XMLSender): ByteArray =
        ByteArrayOutputStream().use {
            getSenderMarshaller().marshal(sender, it)
            it
        }.toByteArray()
}

data class EmottakRequest(
    val tssIdent: String,
    val sender: ByteArray,
    val partnerreferanse: Int,
)
