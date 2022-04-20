package no.nav.syfo.util

import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.helse.msgHead.XMLDocument
import java.security.MessageDigest
import kotlin.math.min

const val MAX_VEDLEGG_SHA_STRING = 40 // ellers blir den konkatenerte sha-strengen for lang for indeksering i Postgres

fun sha256hashstring(
    xmlDocument: XMLDialogmelding,
    vedlegg: List<XMLDocument>,
): String {
    val shaString = sha256hashstringForXMLDocument(xmlDocument)
    val vedleggShaStrings = vedlegg.subList(0, min(vedlegg.size, MAX_VEDLEGG_SHA_STRING)).map { sha256hashstringForXMLDocument(it) }
    return shaString + vedleggShaStrings.joinToString("")
}

private fun sha256hashstringForXMLDocument(xmlDocument: Any) =
    MessageDigest.getInstance("SHA-256")
        .digest(objectMapper.writeValueAsBytes(xmlDocument))
        .fold("") { str, it -> str + "%02x".format(it) }
