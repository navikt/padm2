package no.nav.syfo.client

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLSender
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.logger
import no.nav.syfo.util.*
import java.io.ByteArrayOutputStream
import java.io.IOException

class EmottakSubscriptionClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val endpointUrl: String,
    private val endpointClientId: String,
    private val httpClient: HttpClient
) {

    suspend fun startSubscription(
        samhandlerPraksis: SamhandlerPraksis,
        msgHead: XMLMsgHead,
        receiverBlock: XMLMottakenhetBlokk,
        loggingMeta: LoggingMeta,
    ) {
        val token = azureAdV2Client.getSystemToken(endpointClientId)
            ?: throw RuntimeException("Failed to start subscription: No token was found")
        val request = SubscriptionRequest(
            tssIdent = samhandlerPraksis.tss_ident,
            partnerId = receiverBlock.partnerReferanse.toInt(),
            data = convertSenderToBase64(msgHead.msgInfo.sender)
        )
        val httpResponse: HttpResponse = httpClient.post("$endpointUrl/api/v1/subscription") {
            contentType(ContentType.Application.Json)
            headers {
                append("Nav-Call-Id", msgHead.msgInfo.msgId)
                append(HttpHeaders.Authorization, bearerHeader(token.accessToken))
            }
            body = request
        }

        if (httpResponse.status != HttpStatusCode.OK) {
            logger.error("Emottak subscription (via isproxy) svarte med kode {} for {}", httpResponse.status, fields(loggingMeta))
            throw IOException("Emottak subscription (via isproxy) med uventet kode ${httpResponse.status} for ${loggingMeta.msgId}}")
        }
    }
}

data class SubscriptionRequest(
    val tssIdent: String,
    val partnerId: Int,
    val data: ByteArray
)

fun convertSenderToBase64(sender: XMLSender): ByteArray =
    ByteArrayOutputStream().use {
        senderMarshaller.marshal(sender, it)
        it
    }.toByteArray()
