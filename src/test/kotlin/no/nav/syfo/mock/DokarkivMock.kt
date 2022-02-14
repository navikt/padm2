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
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse

class DokarkivMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val journalpostResponse = JournalpostResponse(
        journalpostId = "12345678",
        journalstatus = "journalstatus",
        journalpostferdigstilt = true,
        dokumenter = emptyList(),
    )

    val name = "dokarkiv"
    val server = mockDokarkivServer(
        port
    )

    private fun mockDokarkivServer(
        port: Int
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                post {
                    val journalpostRequest = call.receive<JournalpostRequest>()
                    if (journalpostRequest.bruker!!.id != UserConstants.PATIENT_FNR_NO_AKTOER_ID) {
                        call.respond(journalpostResponse)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
            }
        }
    }
}
