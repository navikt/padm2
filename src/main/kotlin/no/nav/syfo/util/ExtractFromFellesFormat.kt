package no.nav.syfo.util

import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.msgHead.XMLIdent
import no.nav.helse.msgHead.XMLMsgHead

fun extractDialogmelding(fellesformat: XMLEIFellesformat): XMLDialogmelding =
    fellesformat.get<XMLMsgHead>().document.first {
        it.refDoc.msgType.v == "XML"
    }.refDoc.content.any[0] as XMLDialogmelding

fun extractVedlegg(msgHead: XMLMsgHead) = msgHead.document.filter {
    it.refDoc.msgType.v == "A" &&
            it.refDoc.mimeType == "application/pdf" ||
            it.refDoc.mimeType == "image/tiff" ||
            it.refDoc.mimeType == "image/png" ||
            it.refDoc.mimeType == "image/jpeg"
}

fun extractOrganisationNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
        it.typeId.v == "ENH"
    }

fun extractOrganisationHerNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
        it.typeId.v == "HER"
    }

fun extractSenderOrganisationName(fellesformat: XMLEIFellesformat): String =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation?.organisationName ?: ""

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T
