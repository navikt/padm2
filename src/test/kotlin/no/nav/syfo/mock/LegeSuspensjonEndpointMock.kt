package no.nav.syfo.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpResponseData
import no.nav.syfo.client.Suspendert

fun MockRequestHandleScope.legeSuspensjonMockResponse(): HttpResponseData =
    respond(Suspendert(false))
