package no.nav.syfo.mock

import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.TSSident
import no.nav.syfo.getRandomPort
import no.nav.syfo.util.configure

class SmtssMock {
    private val port = getRandomPort()
    private val emottakPath = "/api/v1/samhandler/emottak"
    private val infotrygdPath = "/api/v1/samhandler/infotrygd"
    val url = "http://localhost:$port"

    val name = "smtss"
    val server = mockSmtssServer(
        port
    )

    private fun mockSmtssServer(
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
                get(emottakPath) {
                    call.respond(TSSident("123"))
                }
                get(infotrygdPath) {
                    call.respond(TSSident("123"))
                }
            }
        }
    }
}
