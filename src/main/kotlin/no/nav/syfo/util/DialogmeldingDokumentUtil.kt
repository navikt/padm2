package no.nav.syfo.util

import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.helse.msgHead.XMLDocument
import no.nav.helse.msgHead.XMLPatient
import java.security.MessageDigest
import kotlin.math.min

const val MAX_VEDLEGG_SHA_STRING = 40 // ellers blir den konkatenerte sha-strengen for lang for indeksering i Postgres

fun sha256hashstring(
    xmlDocument: XMLDialogmelding,
    xmlPatient: XMLPatient,
    vedlegg: List<XMLDocument>,
): String {
    val shaString = sha256hashstringForXMLDocument(xmlDocument)
    val shaStringPatient = sha256hashstringForXMLDocument(xmlPatient)
    val vedleggShaStrings = vedlegg.subList(0, min(vedlegg.size, MAX_VEDLEGG_SHA_STRING)).map { sha256hashstringForXMLDocument(it) }
    return shaString + shaStringPatient + vedleggShaStrings.joinToString("")
}

private fun sha256hashstringForXMLDocument(xmlDocument: Any) =
    MessageDigest.getInstance("SHA-256")
        .digest(objectMapper.writeValueAsBytes(xmlDocument))
        .fold("") { str, it -> str + "%02x".format(it) }
