package no.nav.syfo.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.UserConstants
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse

val dokarkivJournalpostResponse = JournalpostResponse(
    journalpostId = "12345678",
    journalstatus = "journalstatus",
    journalpostferdigstilt = true,
    dokumenter = emptyList(),
)

suspend fun MockRequestHandleScope.dokarkivMockResponse(request: HttpRequestData): HttpResponseData {
    val journalpostRequest = request.receiveBody<JournalpostRequest>()
    return if (
        journalpostRequest.bruker!!.id != UserConstants.PATIENT_FNR_NO_AKTOER_ID &&
        !journalpostRequest.avsenderMottaker!!.id!!.contains('-')
    ) {
        if (journalpostRequest.bruker!!.id == UserConstants.PATIENT_FNR_JP_CONFLICT) {
            respond(dokarkivJournalpostResponse, HttpStatusCode.Conflict)
        } else {
            respond(dokarkivJournalpostResponse)
        }
    } else {
        respond(content = "", status = HttpStatusCode.InternalServerError, headers = headersOf())
    }
}
