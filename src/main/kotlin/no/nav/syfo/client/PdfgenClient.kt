package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.util.retry
import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.model.PdfModel
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.sanitizeForPdfGen

@KtorExperimentalAPI
class PdfgenClient constructor(
    private val url: String,
    private val httpClient: HttpClient
) {
    suspend fun createPdf(payload: PdfModel): ByteArray = retry("pdfgen") {
        httpClient.get(url) {
            contentType(ContentType.Application.Json)
            method = HttpMethod.Post
            body = payload.sanitizeForPdfGen()
        }
    }
}

fun createPdfPayload(
    dialogmelding: Dialogmelding,
    validationResult: ValidationResult,
    pasientFnr: String,
    pasientNavn: String,
    navnSignerendeLege: String,
    antallVedlegg: Int,
): PdfModel = PdfModel(
    dialogmelding = dialogmelding,
    validationResult = validationResult,
    pasientFnr = pasientFnr,
    pasientNavn = pasientNavn,
    navnSignerendeLege = navnSignerendeLege,
    antallVedlegg,
)
