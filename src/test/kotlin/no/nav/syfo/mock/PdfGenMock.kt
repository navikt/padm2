package no.nav.syfo.mock

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.UserConstants
import no.nav.syfo.getRandomPort
import no.nav.syfo.model.PdfModel
import no.nav.syfo.util.configure

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
        port: Int,
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            install(ContentNegotiation) {
                jackson(ContentType.Any) {
                    configure()
                }
            }
            routing {
                post {
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
