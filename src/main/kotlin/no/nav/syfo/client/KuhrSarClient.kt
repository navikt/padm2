package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLSender
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.*
import java.io.ByteArrayOutputStream
import java.io.IOException

class KuhrSarClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val kuhrSarClientId: String,
    private val kuhrSarUrl: String,
    private val httpClient: HttpClient
) {
    suspend fun getTssId(
        legeIdent: PersonIdent,
        partnerId: Int?,
        legekontorOrgName: String,
        legekontorHerId: String?,
        msgHead: XMLMsgHead,
    ): String = retry("get_tssid") {
        val token = azureAdV2Client.getSystemToken(kuhrSarClientId)
            ?: throw RuntimeException("Failed to send request to KuhrSar: No token was found")
        val response: HttpResponse = httpClient.get("$kuhrSarUrl/api/v1/kuhrsar") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
            body = KuhrsarRequest(
                behandlerIdent = legeIdent,
                partnerId = partnerId,
                legekontorOrgName = legekontorOrgName,
                legekontorHerId = legekontorHerId,
                data = convertSenderToBase64(msgHead.msgInfo.sender),
            )
        }
        when (response.status) {
            HttpStatusCode.OK -> response.receive<KuhrsarResponse>().tssId
            else -> throw IOException("Vi fikk en uventet feil fra kuhrSar, prøver på nytt! ${response.content}")
        }
    }
}
data class KuhrsarRequest(
    val behandlerIdent: PersonIdent,
    val partnerId: Int?,
    val legekontorOrgName: String,
    val legekontorHerId: String?,
    val data: ByteArray,
)

data class KuhrsarResponse(
    val tssId: String,
)

fun convertSenderToBase64(sender: XMLSender): ByteArray =
    ByteArrayOutputStream().use {
        senderMarshaller.marshal(sender, it)
        it
    }.toByteArray()
