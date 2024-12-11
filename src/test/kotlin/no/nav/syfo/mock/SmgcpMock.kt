package no.nav.syfo.mock

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.getRandomPort
import no.nav.syfo.util.configure

class SmgcpMock {
    private val port = getRandomPort()
    private val path = "/emottak/startsubscription"
    val url = "http://localhost:$port"

    val name = "smgcp"
    val server = mockSmgcpServer(
        port
    )

    private fun mockSmgcpServer(
        port: Int
    ): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            install(ContentNegotiation) {
                jackson { configure() }
            }
            routing {
                post(path) {
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}
