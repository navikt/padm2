package no.nav.syfo.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.UserConstants.MSG_ID_IN_BEHANDLERDIALOG
import no.nav.syfo.UserConstants.MSG_ID_BEHANDLERDIALOG_WITH_ERROR

fun MockRequestHandleScope.behandlerdialogMockResponse(request: HttpRequestData): HttpResponseData {
    val msgId = request.url.segments.last()
    return when (msgId) {
        MSG_ID_IN_BEHANDLERDIALOG -> respond(content = "", status = HttpStatusCode.OK, headers = headersOf())
        MSG_ID_BEHANDLERDIALOG_WITH_ERROR -> respond(content = "", status = HttpStatusCode.InternalServerError, headers = headersOf())
        else -> respond(content = "", status = HttpStatusCode.NoContent, headers = headersOf())
    }
}
