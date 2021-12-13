package no.nav.syfo.mock

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.client.*
import no.nav.syfo.getRandomPort

class StsMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val name = "sts"
    val server = mockSts(
        port
    )

    private fun mockSts(
        port: Int
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                get {
                    call.respond(
                        OidcToken(
                            access_token = "token",
                            token_type = "tokenType",
                            expires_in = 3600,
                        )
                    )
                }
            }
        }
    }
}
