package no.nav.syfo.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*

fun MockRequestHandleScope.smgcpMockResponse(): HttpResponseData =
    respond(content = "", status = HttpStatusCode.OK, headers = headersOf())
