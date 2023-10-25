package no.nav.syfo.mock

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.UserConstants.MSG_ID_IN_MODIA
import no.nav.syfo.getRandomPort
import no.nav.syfo.util.configure

class BehandlerdialogMock {
    private val port = getRandomPort()
    private val path = "/api/system/v1/meldinger"
    val url = "http://localhost:$port"
    private val msgIdParam = "msgId"

    val name = "isbehandlerdialog"
    val server = mockBehandlerdialogServer(port)

    private fun mockBehandlerdialogServer(
        port: Int
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            install(ContentNegotiation) {
                jackson { configure() }
            }
            routing {
                get("$path/{$msgIdParam}") {
                    val msgId = call.parameters[msgIdParam]
                    if (msgId == MSG_ID_IN_MODIA) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}
