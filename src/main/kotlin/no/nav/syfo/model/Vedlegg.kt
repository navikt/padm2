package no.nav.syfo.model

import no.nav.helse.base64container.Base64Container
import no.nav.helse.msgHead.XMLDocument
import no.nav.syfo.logger
import no.nav.syfo.util.convertImageToPDF
import java.io.ByteArrayOutputStream
import java.util.HexFormat

val GYLDIGE_VEDLEGG_MIME_TYPER = listOf("application/pdf", "image/tiff", "image/png", "image/jpeg", "image/pjpeg", "image/jpg", "image/pjpg")

private val PDF_MAGIC_NUMBER: ByteArray = HexFormat.of().parseHex("255044462D") // binary "%PDF-"

data class Vedlegg(
    val mimeType: String,
    val beskrivelse: String,
    val contentBase64: ByteArray
)

fun Vedlegg.erGyldig(): Boolean =
    mimeType in GYLDIGE_VEDLEGG_MIME_TYPER && innholdStemmerMedMimeType(mimeType, contentBase64)

fun innholdStemmerMedMimeType(mimeType: String, innhold: ByteArray): Boolean =
    mimeType != "application/pdf" || innhold.copyOf(PDF_MAGIC_NUMBER.size) contentEquals PDF_MAGIC_NUMBER

fun XMLDocument.toVedlegg(): Vedlegg {
    return Vedlegg(
        mimeType = refDoc.mimeType,
        beskrivelse = refDoc.description ?: "",
        contentBase64 = toBase64Container().value
    )
}

fun XMLDocument.toBase64Container() = refDoc.content.any[0] as Base64Container

fun Vedlegg.toPDFVedlegg(): Vedlegg {
    if (findFiltype() == "PDFA") return this

    logger.info("Converting vedlegg of type ${this.mimeType} to PDFA")

    val image = ByteArrayOutputStream().use { outputStream ->
        convertImageToPDF(this.contentBase64.inputStream(), outputStream)
        outputStream.toByteArray()
    }

    return Vedlegg(
        "application/pdf",
        this.beskrivelse,
        image,
    )
}

fun Vedlegg.findFiltype(): String =
    when (this.mimeType) {
        "application/pdf" -> "PDFA"
        "image/tiff" -> "TIFF"
        "image/png" -> "PNG"
        "image/jpeg" -> "JPEG"
        "image/pjpeg" -> "JPEG"
        "image/jpg" -> "JPEG"
        "image/pjpg" -> "JPEG"
        else -> throw RuntimeException("Vedlegget er av ukjent mimeType ${this.mimeType}")
    }
