package no.nav.syfo.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.UserConstants
import no.nav.syfo.client.*
import no.nav.syfo.model.HelsepersonellKategori

fun MockRequestHandleScope.syfohelsenettproxyMockResponse(request: HttpRequestData): HttpResponseData {
    val behandlerFnr = request.headers["behandlerFnr"]
    return if (behandlerFnr == UserConstants.BEHANDLER_FNR_UKJENT) {
        respond(content = "", status = HttpStatusCode.NotFound, headers = headersOf())
    } else {
        respond(
            HelsenettProxyBehandler(
                godkjenninger = listOf(
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
