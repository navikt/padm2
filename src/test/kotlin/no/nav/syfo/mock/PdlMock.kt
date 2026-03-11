package no.nav.syfo.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.UserConstants.PATIENT_FNR_NO_AKTOER_ID
import no.nav.syfo.client.pdl.*

class PdlMockState {
    var pdlAlwaysFail = false
}

suspend fun MockRequestHandleScope.pdlMockResponse(
    request: HttpRequestData,
    state: PdlMockState,
): HttpResponseData {
    if (state.pdlAlwaysFail) {
        return respond(content = "", status = HttpStatusCode.ServiceUnavailable, headers = headersOf())
    }
    val pdlRequest = request.receiveBody<PdlHentIdenterRequest>()
    val personIdentNumber = pdlRequest.variables.ident
    val response = if (personIdentNumber == PATIENT_FNR_NO_AKTOER_ID) {
        generatePdlIdenterResponse(
            identValueTypeList = emptyList(),
            errors = listOf(
                PdlError(
                    message = "ikke funnet",
                    locations = emptyList(),
                    path = emptyList(),
                    extensions = PdlErrorExtension("", ""),
                )
            ),
        )
    } else {
        generatePdlIdenterResponse(
            identValueTypeList = listOf(
                Pair(personIdentNumber, IdentType.FOLKEREGISTERIDENT),
                Pair("10$personIdentNumber", IdentType.AKTORID),
            ),
        )
    }
    return respond(response)
}

fun generatePdlIdenterResponse(
    identValueTypeList: List<Pair<String, IdentType>>,
    errors: List<PdlError> = emptyList(),
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
