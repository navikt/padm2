package no.nav.syfo.mock

import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.UserConstants
import no.nav.syfo.client.*
import no.nav.syfo.getRandomPort
import no.nav.syfo.model.HelsepersonellKategori
import no.nav.syfo.util.configure

class SyfohelsenettproxyMock {
    private val port = getRandomPort()
    private val path = "/api/v2/behandler"
    val url = "http://localhost:$port"

    val name = "syfohelsenettproxy"
    val server = mockSyfohelsenettproxy(
        port
    )

    private fun mockSyfohelsenettproxy(
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
                get(path) {
                    val behandlerFnr = call.request.headers["behandlerFnr"]
                    call.respond(
                        HelsenettProxyBehandler(
                            godkjenninger =
                            listOf(
                                Godkjenning(
                                    autorisasjon = Kode(
                                        aktiv = behandlerFnr != UserConstants.BEHANDLER_FNR_IKKE_AUTORISERT,
                                        oid = 7704,
                                        verdi = "1",
                                    ),
                                    helsepersonellkategori = Kode(
                                        aktiv = behandlerFnr != UserConstants.BEHANDLER_FNR_IKKE_AUTORISERT,
                                        oid = 0,
                                        verdi = HelsepersonellKategori.LEGE.verdi,
                                    ),
                                )
                            ),
                            fornavn = "fornavn",
                            mellomnavn = null,
                            etternavn = "etternavn",
                            fnr = null,
                            hprNummer = 1,
                        )
                    )
                }
            }
        }
    }
}
