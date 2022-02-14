package no.nav.syfo.mock

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import no.nav.syfo.client.installContentNegotiation
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
