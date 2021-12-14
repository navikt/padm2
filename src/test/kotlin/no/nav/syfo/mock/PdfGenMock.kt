package no.nav.syfo.mock

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.getRandomPort

class PdfGenMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val name = "pdfgen"
    val server = mockPdfGenServer(
        port
    )

    private fun mockPdfGenServer(
        port: Int
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            routing {
                post() {
                    call.respond(byteArrayOf(0x2E, 0x28))
                }
            }
        }
    }
}
