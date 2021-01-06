package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.model.PdfModel
import no.nav.syfo.model.ValidationResult

@KtorExperimentalAPI
class PdfgenClient constructor(
    private val url: String,
    private val httpClient: HttpClient
) {
    suspend fun createPdf(payload: PdfModel): ByteArray = retry("pdfgen") {
        httpClient.get<ByteArray>(url) {
            contentType(ContentType.Application.Json)
            method = HttpMethod.Post
            body = payload
        }
    }
}

fun createPdfPayload(
    dialogmelding: Dialogmelding,
    validationResult: ValidationResult,
    pasientFnr: String,
    pasientNavn: String,
    navnSignerendeLege: String
): PdfModel = PdfModel(
    dialogmelding = dialogmelding,
    validationResult = validationResult,
    pasientFnr = pasientFnr,
    pasientNavn = pasientNavn,
    navnSignerendeLege = navnSignerendeLege
)
