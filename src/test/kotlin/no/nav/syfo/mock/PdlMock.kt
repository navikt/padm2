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
import no.nav.syfo.UserConstants.PATIENT_FNR_NO_AKTOER_ID
import no.nav.syfo.client.*
import no.nav.syfo.client.pdl.*
import no.nav.syfo.getRandomPort

class PdlMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    var pdlAlwaysFail = false

    val name = "pdl"
    val server = mockPdlServer(
        port
    )

    private fun mockPdlServer(
        port: Int
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                post() {
                    if (pdlAlwaysFail) {
                        call.respond(HttpStatusCode.ServiceUnavailable)
                    } else {
                        val pdlRequest = call.receive<PdlHentIdenterRequest>()
                        val personIdentNumber = pdlRequest.variables.ident
                        val response = if (personIdentNumber == PATIENT_FNR_NO_AKTOER_ID) {
                            generatePdlIdenterResponse(
                                identValueTypeList = emptyList(),
                                errors = listOf(
                                    PdlError(
                                        message = "ikke funnet",
                                        locations = emptyList(),
                                        path = emptyList(),
                                        extensions = PdlErrorExtension("", "")
                                    )
                                )
                            )
                        } else {
                            val identValueTypeList = listOf(
                                Pair(personIdentNumber, IdentType.FOLKEREGISTERIDENT),
                                Pair("10$personIdentNumber", IdentType.AKTORID),
                            )
                            generatePdlIdenterResponse(
                                identValueTypeList = identValueTypeList,
                            )
                        }
                        call.respond(response)
                    }
                }
            }
        }
    }

    fun generatePdlIdenterResponse(
        identValueTypeList: List<Pair<String, IdentType>>,
        errors: List<PdlError> = emptyList()
    ) = PdlIdenterResponse(
        data = PdlHentIdenter(
            hentIdenter = PdlIdenter(
                identer = identValueTypeList.map { (ident, type) ->
                    PdlIdent(
                        ident = ident,
                        historisk = false,
                        gruppe = type.name,
                    )
                }
            ),
        ),
        errors = errors,
    )
}
