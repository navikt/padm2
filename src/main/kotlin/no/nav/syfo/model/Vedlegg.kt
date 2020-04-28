package no.nav.syfo.model

data class Vedlegg(
    val mimeType: String,
    val beskrivelse: String,
    val contentBase64: ByteArray
)
