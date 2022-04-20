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
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse
import no.nav.syfo.util.configure

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
            install(ContentNegotiation) {
                jackson { configure() }
            }
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
