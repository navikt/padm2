package no.nav.syfo.mock

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.client.*
import no.nav.syfo.getRandomPort

class SyfohelsenettproxyMock {
    private val port = getRandomPort()
    private val path = "/api/v2/behandler"
    val url = "http://localhost:$port"

    val name = "syfohelsenettproxy"
    val server = mockSyfohelsenettproxy(
        port
    )

    private fun mockSyfohelsenettproxy(
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
                        HelsenettProxyBehandler(
                            godkjenninger = emptyList(),
                            fornavn = "fornavn",
                            mellomnavn = null,
                            etternavn = "etternavn",
                            fnr = null,
                            hprNummer = 1,
                        )
                    )
                }
            }
        }
    }
}
