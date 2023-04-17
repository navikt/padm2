package no.nav.syfo.model

import no.nav.helse.base64container.Base64Container
import no.nav.helse.msgHead.XMLDocument
import no.nav.syfo.logger
import no.nav.syfo.util.ImageToPDF
import java.io.ByteArrayOutputStream

data class Vedlegg(
    val mimeType: String,
    val beskrivelse: String,
    val contentBase64: ByteArray
)

fun XMLDocument.toVedlegg(): Vedlegg {
    val base64Container = refDoc.content.any[0] as Base64Container

    return Vedlegg(
        mimeType = refDoc.mimeType,
        beskrivelse = refDoc.description ?: "",
        contentBase64 = base64Container.value
    )
}

fun Vedlegg.toPDFVedlegg(): Vedlegg {
    if (findFiltype() == "PDFA") return this

    logger.info("Converting vedlegg of type ${this.mimeType} to PDFA")

    val image = ByteArrayOutputStream().use { outputStream ->
        ImageToPDF(this.contentBase64.inputStream(), outputStream)
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
        "image/jpg" -> "JPEG"
        else -> throw RuntimeException("Vedlegget er av ukjent mimeType ${this.mimeType}")
    }
