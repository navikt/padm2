package no.nav.syfo.util

import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.msgHead.*
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.getName
import no.nav.syfo.logger

fun extractDialogmelding(fellesformat: XMLEIFellesformat): XMLDialogmelding =
    fellesformat.get<XMLMsgHead>().document.first {
        it.refDoc.msgType.v == "XML"
    }.refDoc.content.any[0] as XMLDialogmelding

fun extractVedlegg(fellesformat: XMLEIFellesformat) = fellesformat.get<XMLMsgHead>().document.filter {
    it.isVedlegg()
}

fun XMLDocument.isVedlegg(): Boolean {
    return this.refDoc.msgType.v == "A" &&
        listOf("application/pdf", "image/tiff", "image/png", "image/jpeg", "image/jpg").contains(this.refDoc.mimeType)
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

fun extractLegeHpr(fellesformat: XMLEIFellesformat): String? {
    val hpr = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation?.healthcareProfessional?.ident?.find {
        it.typeId.v == "HPR"
    }?.id
    return if (isValidHpr(hpr)) hpr else null
}

private fun isValidHpr(hprNr: String?) =
    hprNr != null && hprNr.length <= 9 && hprNr.all { char -> char.isDigit() }

fun no.nav.helse.dialogmelding.XMLHealthcareProfessional.toBehandler(): Behandler = Behandler(
    fornavn = givenName ?: "",
    etternavn = familyName,
    mellomnavn = middleName ?: null
)

fun no.nav.helse.msgHead.XMLHealthcareProfessional.toBehandler(): Behandler = Behandler(
    fornavn = givenName ?: "",
    etternavn = familyName,
    mellomnavn = middleName ?: null
)

fun extractBehandler(fellesformat: XMLEIFellesformat): Behandler? {
    val behandlerInMsgHead = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation?.healthcareProfessional?.toBehandler() ?: null

    return if (behandlerInMsgHead != null) behandlerInMsgHead else {
        val rollerListe = extractDialogmelding(fellesformat).notat.first().rollerRelatertNotat
        if (rollerListe.isNullOrEmpty()) null else rollerListe.first().healthcareProfessional?.toBehandler() ?: null
    }
}

fun extractBehandlerNavn(fellesformat: XMLEIFellesformat): String? {
    val behandler = extractBehandler(fellesformat)
    return behandler?.getName()
}

fun extractPasientNavn(fellesformat: XMLEIFellesformat): String =
    if (fellesformat.get<XMLMsgHead>().msgInfo.patient.middleName == null)
        "${fellesformat.get<XMLMsgHead>().msgInfo.patient.familyName}, " +
            "${fellesformat.get<XMLMsgHead>().msgInfo.patient.givenName}" else
        "${fellesformat.get<XMLMsgHead>().msgInfo.patient.familyName}, " +
            "${fellesformat.get<XMLMsgHead>().msgInfo.patient.givenName} " +
            "${fellesformat.get<XMLMsgHead>().msgInfo.patient.middleName}"

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun extractInnbyggerident(fellesformat: XMLEIFellesformat): String? =
    fellesformat.get<XMLMsgHead>().msgInfo.patient.ident.find {
        it.typeId.v == "FNR" || it.typeId.v == "DNR"
    }?.id

fun extractIdentFromBehandler(fellesformat: XMLEIFellesformat): String? {
    val behandlerIdent = fellesformat.get<XMLMsgHead>()
        .msgInfo.sender.organisation?.healthcareProfessional?.ident?.find {
            it.typeId.v == "FNR" || it.typeId.v == "DNR"
        }?.id

    if (behandlerIdent == null) {
        logger.info("Behandler did not include ident type fnr or dnr in dialogmelding")
    }
    return behandlerIdent
}
