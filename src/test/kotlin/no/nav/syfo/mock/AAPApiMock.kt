package no.nav.syfo.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.UserConstants.MSG_ID_IN_KELVIN
import no.nav.syfo.UserConstants.MSG_ID_IN_KELVIN_WITH_ERROR
import no.nav.syfo.client.aapapi.AAPResponse

fun MockRequestHandleScope.aapApiMockResponse(request: HttpRequestData): HttpResponseData {
    val msgId = request.url.segments.get(request.url.segments.size - 2)
    return when (msgId) {
        MSG_ID_IN_KELVIN -> respond(body = eksisterer, statusCode = HttpStatusCode.OK)
        MSG_ID_IN_KELVIN_WITH_ERROR -> respond(content = "", status = HttpStatusCode.InternalServerError, headers = headersOf())
        else -> respond(body = eksistererIkke, statusCode = HttpStatusCode.NoContent)
    }
}

val eksisterer = AAPResponse(true)
val eksistererIkke = AAPResponse(false)
