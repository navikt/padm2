package no.nav.syfo.mock

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.client.*
import no.nav.syfo.getRandomPort

class SubscriptionEndpointMock {
    private val port = getRandomPort()
    private val path = "/api/v1/subscription"
    val url = "http://localhost:$port"

    val name = "subscriptionEndpoint"
    val server = mockSubscriptionEndpoint(
        port
    )

    private fun mockSubscriptionEndpoint(
        port: Int
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                post(path) {
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}
