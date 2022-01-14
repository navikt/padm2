package no.nav.syfo.util

import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.helse.msgHead.XMLDocument
import java.security.MessageDigest

fun sha256hashstring(
    xmlDocument: XMLDialogmelding,
    vedlegg: List<XMLDocument>,
): String {
    val shaString = sha256hashstringForXMLDocument(xmlDocument)
    val vedleggShaStrings = vedlegg.map { sha256hashstringForXMLDocument(it) }
    return shaString + vedleggShaStrings.joinToString("")
}

private fun sha256hashstringForXMLDocument(xmlDocument: Any) =
    MessageDigest.getInstance("SHA-256")
        .digest(objectMapper.writeValueAsBytes(xmlDocument))
        .fold("") { str, it -> str + "%02x".format(it) }
