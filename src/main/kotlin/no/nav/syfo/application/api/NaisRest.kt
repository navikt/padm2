package no.nav.syfo.application.api

import io.ktor.server.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.metrics.METRICS_REGISTRY

fun Routing.registerNaisApi(
    applicationState: ApplicationState,
    readynessCheck: () -> Boolean = { applicationState.ready },
    alivenessCheck: () -> Boolean = { applicationState.alive },
) {
    get("/is_alive") {
        if (alivenessCheck()) {
            call.respondText("I'm alive! :)")
        } else {
            call.respondText("I'm dead x_x", status = HttpStatusCode.InternalServerError)
        }
    }
    get("/is_ready") {
        if (readynessCheck()) {
            call.respondText("I'm ready! :)")
        } else {
            call.respondText("Please wait! I'm not ready :(", status = HttpStatusCode.InternalServerError)
        }
    }
    get("/prometheus") {
        call.respondText(METRICS_REGISTRY.scrape())
    }
}
