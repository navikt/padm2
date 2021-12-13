package no.nav.syfo.model

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.logger
import no.nav.syfo.metrics.SANITIZE_INVALID_CHAR_COUNTER
import no.nav.syfo.util.objectMapper

data class PdfModel(
    val dialogmelding: Dialogmelding,
    val validationResult: ValidationResult,
    val pasientFnr: String,
    val pasientNavn: String,
    val navnSignerendeLege: String,
    val antallVedlegg: Int,
)

const val BYTE_ORDER_MARK = '\uFEFF'
const val INVALID_CHAR = '\uFFFE'
const val NUL = 0.toChar()

val illegalCharacters = listOf(BYTE_ORDER_MARK, INVALID_CHAR)

fun PdfModel.sanitizeForPdfGen(): PdfModel {
    val pdfModelJsonString = objectMapper.writeValueAsString(this)

    val sanitizedJson = pdfModelJsonString.toCharArray().filter {
        if (it in illegalCharacters || it < NUL) {
            SANITIZE_INVALID_CHAR_COUNTER.inc()
            logger.warn("Illegal character in PdfModel: %x".format(it.code))
            false
        } else {
            true
        }
    }.joinToString("")

    return objectMapper.readValue(sanitizedJson)
}
