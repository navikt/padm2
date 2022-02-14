package no.nav.syfo.mock


import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import no.nav.syfo.UserConstants
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.HelsenettProxyBehandler
import no.nav.syfo.client.Kode
import no.nav.syfo.client.installContentNegotiation
import no.nav.syfo.getRandomPort
import no.nav.syfo.model.HelsepersonellKategori

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
            installContentNegotiation()
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
