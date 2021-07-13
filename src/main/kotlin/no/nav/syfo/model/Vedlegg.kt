package no.nav.syfo.model

import no.nav.helse.base64container.Base64Container
import no.nav.helse.msgHead.XMLDocument

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
