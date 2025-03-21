package no.nav.syfo.validation

import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.syfo.model.DialogmeldingKodeverk
import no.nav.syfo.model.DialogmeldingType
import no.nav.syfo.model.findDialogmeldingKodeverk
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("no.nav.syfo.padm2")

fun isKodeverkValid(msgId: String, xmlDialogmelding: XMLDialogmelding, dialogmeldingType: DialogmeldingType): Boolean {
    when (dialogmeldingType) {
        DialogmeldingType.DIALOGMELDING_DIALOGMOTE_INNKALLING_MOTERESPONS -> {
            val kodeverkOID = xmlDialogmelding.notat.first().temaKodet.s
            val versjon = xmlDialogmelding.notat.first().temaKodet.v
            val xmlDialogmeldingKodeverk = findDialogmeldingKodeverk(kodeverkOID, versjon)
            val gyldigeDialogmeldingKodeverk = DialogmeldingType.DIALOGMELDING_DIALOGMOTE_INNKALLING_MOTERESPONS.dialogmeldingKodeverk
            return gyldigeDialogmeldingKodeverk.contains(xmlDialogmeldingKodeverk)
        }
        DialogmeldingType.DIALOGMELDING_FORESPORSEL_FRA_SAKSBEHANDLER_FORESPORSEL_SVAR -> {
            val kodeverkOID = xmlDialogmelding.notat.first().temaKodet.s
            val versjon = xmlDialogmelding.notat.first().temaKodet.v
            val xmlDialogmeldingKodeverk = findDialogmeldingKodeverk(kodeverkOID, versjon)
            val foresporselKodeverk = xmlDialogmelding.notat.first().foresporsel?.typeForesp?.s
            if (foresporselKodeverk == DialogmeldingKodeverk.INNKALLING_DIALOGMOTE_KODEVERK.kodeverkOID) {
                logger.warn("Invalid kodeverk: Received foresporselsvar for foresporsel with dialogmotekodeverk: $msgId")
                return false
            }
            val gyldigeDialogmeldingKodeverk = DialogmeldingType.DIALOGMELDING_FORESPORSEL_FRA_SAKSBEHANDLER_FORESPORSEL_SVAR.dialogmeldingKodeverk
            return gyldigeDialogmeldingKodeverk.contains(xmlDialogmeldingKodeverk)
        }
        DialogmeldingType.DIALOGMELDING_HENVENDELSE_FRA_LEGE_HENDVENDELSE -> {
            val kodeverkOID = xmlDialogmelding.notat.first().temaKodet.s
            val versjon = xmlDialogmelding.notat.first().temaKodet.v
            val xmlDialogmeldingKodeverk = findDialogmeldingKodeverk(kodeverkOID, versjon)
            val gyldigeDialogmeldingKodeverk = DialogmeldingType.DIALOGMELDING_HENVENDELSE_FRA_LEGE_HENDVENDELSE.dialogmeldingKodeverk
            return gyldigeDialogmeldingKodeverk.contains(xmlDialogmeldingKodeverk)
        }
        else -> {
            return false
        }
    }
}
