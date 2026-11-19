package no.nav.syfo.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.UserConstants.MSG_ID_IN_SPEIL
import no.nav.syfo.client.sporhund.SporhundResponse

fun MockRequestHandleScope.sporhundMockResponse(request: HttpRequestData): HttpResponseData {
    val msgId = request.url.segments[request.url.segments.size - 2]
    return when (msgId) {
        MSG_ID_IN_SPEIL -> respond(body = SporhundResponse(true), statusCode = HttpStatusCode.OK)
        else -> respond(body = SporhundResponse(false), statusCode = HttpStatusCode.OK)
    }
}
