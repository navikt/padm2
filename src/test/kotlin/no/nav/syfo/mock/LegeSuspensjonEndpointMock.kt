package no.nav.syfo.mock

import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.*
import no.nav.syfo.getRandomPort
import no.nav.syfo.util.configure

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
            install(ContentNegotiation) {
                jackson { configure() }
            }
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
