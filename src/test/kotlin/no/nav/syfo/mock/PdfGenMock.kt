package no.nav.syfo.mock

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.UserConstants
import no.nav.syfo.client.installContentNegotiation
import no.nav.syfo.getRandomPort
import no.nav.syfo.model.PdfModel

class PdfGenMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val name = "pdfgen"
    var alwaysFail = false
    var allowFail = true
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
            installContentNegotiation()
            routing {
                post() {
                    val pdfModel = call.receive<PdfModel>()
                    if (alwaysFail || (allowFail && pdfModel.pasientFnr == UserConstants.PATIENT_FNR_PDFGEN_FAIL)) {
                        call.respond(HttpStatusCode.BadRequest)
                    } else {
                        call.respond(byteArrayOf(0x2E, 0x28))
                    }
                }
            }
        }
    }
}
