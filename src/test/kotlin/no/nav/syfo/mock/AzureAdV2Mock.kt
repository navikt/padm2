package no.nav.syfo.mock


import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import no.nav.syfo.UserConstants
import no.nav.syfo.client.azuread.v2.AzureAdV2TokenResponse
import no.nav.syfo.client.installContentNegotiation
import no.nav.syfo.getRandomPort

class AzureAdV2Mock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val aadV2TokenResponse = AzureAdV2TokenResponse(
        access_token = UserConstants.AZUREAD_TOKEN,
        expires_in = 3600,
        token_type = "type"
    )

    val name = "azureadv2"
    val server = mockAzureAdV2Server(port = port)

    private fun mockAzureAdV2Server(
        port: Int
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                post {
                    call.respond(aadV2TokenResponse)
                }
            }
        }
    }
}
