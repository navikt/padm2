package no.nav.syfo.mock

import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.UserConstants
import no.nav.syfo.client.azuread.v2.AzureAdV2TokenResponse
import no.nav.syfo.getRandomPort
import no.nav.syfo.util.configure

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
        port: Int,
    ): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            install(ContentNegotiation) {
                jackson { configure() }
            }
            routing {
                post {
                    call.respond(aadV2TokenResponse)
                }
            }
        }
    }
}
