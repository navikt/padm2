package no.nav.syfo.mock

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
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
