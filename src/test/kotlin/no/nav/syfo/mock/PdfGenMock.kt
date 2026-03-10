package no.nav.syfo.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.UserConstants
import no.nav.syfo.model.PdfModel

class PdfGenMockState {
    var alwaysFail = false
    var allowFail = true
}

suspend fun MockRequestHandleScope.pdfGenMockResponse(
    request: HttpRequestData,
    state: PdfGenMockState,
): HttpResponseData {
    val pdfModel = request.receiveBody<PdfModel>()
    return if (state.alwaysFail || (state.allowFail && pdfModel.pasientFnr == UserConstants.PATIENT_FNR_PDFGEN_FAIL)) {
        respond(content = "", status = HttpStatusCode.BadRequest, headers = headersOf())
    } else {
        respond(content = byteArrayOf(0x2E, 0x28))
    }
}
