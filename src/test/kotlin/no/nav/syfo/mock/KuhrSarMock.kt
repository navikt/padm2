package no.nav.syfo.mock

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.client.*
import no.nav.syfo.getRandomPort

class KuhrSarMock {
    private val port = getRandomPort()
    private val path = "/api/v1/kuhrsar"
    val url = "http://localhost:$port"

    val name = "kuhrsar"
    val server = mockKuhrSarServer(
        port
    )

    private fun mockKuhrSarServer(
        port: Int
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                get(path) {
                    call.respond(KuhrsarResponse("123"))
                }
            }
        }
    }
}
