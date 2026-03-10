package no.nav.syfo.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpResponseData
import no.nav.syfo.client.TssId

fun MockRequestHandleScope.smtssMockResponse(): HttpResponseData =
    respond(TssId("123"))
