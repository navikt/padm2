package no.nav.syfo.mock

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import no.nav.syfo.client.Suspendert
import no.nav.syfo.client.installContentNegotiation
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
