package no.nav.syfo.mock

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.client.*
import no.nav.syfo.getRandomPort

class LegeSuspensjonEndpointMock {
    private val port = getRandomPort()
    private val path = "/api/v1/btsys/suspensjon/status"
    val url = "http://localhost:$port"

    val name = "legeSuspensjonEndpoint"
    val server = mockLegeSuspensjonEndpoint(
        port
    )

    private fun mockLegeSuspensjonEndpoint(
        port: Int
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                get(path) {
                    call.respond(
                        Suspendert(false)
                    )
                }
            }
        }
    }
}
