package no.nav.syfo.model

import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.util.*
import java.time.LocalDateTime
import java.time.ZoneId

data class ReceivedDialogmelding(
    val dialogmelding: Dialogmelding,
    val dialogmeldingType: DialogmeldingType,
    val personNrPasient: String,
    val personNrLege: String,
    val navLogId: String,
    val msgId: String,
    val msgType: String,
    val conversationRef: String?,
    val parentRef: String?,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorOrgName: String,
    val mottattDato: LocalDateTime,
    val source: DialogmeldingSource,
    val fellesformat: String,
) {
    fun loggingMeta() = LoggingMeta(
        mottakId = navLogId,
        orgNr = legekontorOrgNr,
        msgId = msgId,
    )

    companion object {
        fun create(
            dialogmeldingId: String,
            fellesformat: String,
        ): ReceivedDialogmelding {
            val fellesformatXml = safeUnmarshal(fellesformat)
            val emottakblokk = fellesformatXml.get<XMLMottakenhetBlokk>()
            val msgHead: XMLMsgHead = fellesformatXml.get()
            val legeIdent = emottakblokk.avsenderFnrFraDigSignatur
            val legekontorOrgName = extractSenderOrganisationName(fellesformatXml)
            val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformatXml)?.id
            val dialogmeldingXml = extractDialogmelding(fellesformatXml)
            val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)
            val behandlerNavn = extractBehandlerNavn(fellesformatXml)
            val innbyggerIdent = extractInnbyggerident(fellesformatXml)
            val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformatXml)?.id

            val dialogmelding = dialogmeldingXml.toDialogmelding(
                dialogmeldingId = dialogmeldingId,
                dialogmeldingType = dialogmeldingType,
                signaturDato = msgHead.msgInfo.genDate,
                navnHelsePersonellNavn = behandlerNavn,
            )

            return ReceivedDialogmelding(
                dialogmelding = dialogmelding,
                dialogmeldingType = dialogmeldingType,
                personNrPasient = innbyggerIdent!!,
                personNrLege = legeIdent,
                navLogId = emottakblokk.ediLoggId,
                msgId = msgHead.msgInfo.msgId,
                msgType = msgHead.msgInfo.type.v,
                conversationRef = msgHead.msgInfo.conversationRef?.refToConversation,
                parentRef = msgHead.msgInfo.conversationRef?.refToParent,
                legekontorOrgNr = legekontorOrgNr,
                legekontorOrgName = legekontorOrgName,
                legekontorHerId = legekontorHerId,
                mottattDato = emottakblokk.mottattDatotid.toGregorianCalendar().toZonedDateTime()
                    .withZoneSameInstant(
                        ZoneId.of("Europe/Oslo")
                    ).toLocalDateTime(),
                source = DialogmeldingSource.MQ,
                fellesformat = fellesformat,
            )
        }
    }
}
