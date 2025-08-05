package no.nav.syfo.model

import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.logger
import no.nav.syfo.util.*
import java.time.LocalDateTime
import java.time.ZoneId

data class ReceivedDialogmelding(
    val dialogmelding: Dialogmelding,
    val personNrPasient: String,
    val personNrLege: String,
    val navLogId: String,
    val msgId: String,
    val legekontorOrgNr: LegekontorOrgNummer?,
    val legekontorHerId: String?,
    val legekontorOrgName: String,
    val mottattDato: LocalDateTime,
    val legehpr: String?,
    val fellesformat: String,
) {
    companion object {
        fun create(
            dialogmeldingId: String,
            fellesformat: XMLEIFellesformat,
            inputMessageText: String,
        ): ReceivedDialogmelding {
            val emottakblokk = fellesformat.get<XMLMottakenhetBlokk>()
            val msgHead: XMLMsgHead = fellesformat.get()
            val legeIdent = emottakblokk.avsenderFnrFraDigSignatur
            val legekontorOrgName = extractSenderOrganisationName(fellesformat)
            val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
            val dialogmeldingXml = extractDialogmelding(fellesformat)
            val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)
            val legeHpr = extractLegeHpr(dialogmeldingId, fellesformat)
            val behandlerNavn = extractBehandlerNavn(fellesformat)
            val behandlerIdent = extractIdentFromBehandler(fellesformat)
            val innbyggerIdent = extractInnbyggerident(fellesformat)
            val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id

            if (behandlerIdent != legeIdent) {
                logger.info("Behandler and avsender are different in dialogmelding: $dialogmeldingId")
            }

            val dialogmelding = dialogmeldingXml.toDialogmelding(
                dialogmeldingId = dialogmeldingId,
                dialogmeldingType = dialogmeldingType,
                signaturDato = msgHead.msgInfo.genDate,
                navnHelsePersonellNavn = behandlerNavn
            )

            return ReceivedDialogmelding(
                dialogmelding = dialogmelding,
                personNrPasient = innbyggerIdent!!,
                personNrLege = legeIdent,
                navLogId = emottakblokk.ediLoggId,
                msgId = msgHead.msgInfo.msgId,
                legekontorOrgNr = legekontorOrgNr?.let { LegekontorOrgNummer(it) },
                legekontorOrgName = legekontorOrgName,
                legekontorHerId = legekontorHerId,
                mottattDato = emottakblokk.mottattDatotid.toGregorianCalendar().toZonedDateTime()
                    .withZoneSameInstant(
                        ZoneId.of("Europe/Oslo")
                    ).toLocalDateTime(),
                legehpr = legeHpr,
                fellesformat = inputMessageText,
            )
        }
    }
}
