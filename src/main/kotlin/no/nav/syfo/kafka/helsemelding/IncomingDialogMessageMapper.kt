package no.nav.syfo.kafka.helsemelding

import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.model.DialogmeldingSource
import no.nav.syfo.model.DialogmeldingType
import no.nav.syfo.model.ForesporselFraSaksbehandlerForesporselSvar
import no.nav.syfo.model.HenvendelseFraLegeHenvendelse
import no.nav.syfo.model.InnkallingMoterespons
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.findDialogmeldingKodeverk
import no.nav.syfo.model.toTypeMelding
import no.nav.syfo.util.configuredJacksonMapper
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

private const val KODEVERK_OID_PREFIX = "2.16.578.1.12.4.1.1."

private val helsemeldingMapper = configuredJacksonMapper()

fun parseIncomingDialogMessage(kafkaMelding: String): IncomingDialogMessage =
    helsemeldingMapper.readValue(kafkaMelding, IncomingDialogMessage::class.java)

fun receivedDialogmeldingFromHelsemelding(
    dialogmeldingId: String,
    kafkaMelding: String,
): ReceivedDialogmelding =
    parseIncomingDialogMessage(kafkaMelding).toReceivedDialogmelding(
        dialogmeldingId = dialogmeldingId,
        kafkaMelding = kafkaMelding,
    )

fun IncomingDialogMessage.toReceivedDialogmelding(
    dialogmeldingId: String,
    kafkaMelding: String,
): ReceivedDialogmelding {
    val kodeverk = findDialogmeldingKodeverk(
        kodeverkOID = "$KODEVERK_OID_PREFIX${type.codeSystem}",
        v = type.code.toString(),
    ) ?: throw IllegalArgumentException("Fant ikke kodeverk for $type i melding $id")
    val dialogmeldingType = DialogmeldingType.entries.single { kodeverk in it.dialogmeldingKodeverk }
    val temaKode = kodeverk.toTypeMelding()

    val dialogmelding = Dialogmelding(
        id = dialogmeldingId,
        innkallingMoterespons = if (dialogmeldingType == DialogmeldingType.DIALOGMELDING_DIALOGMOTE_INNKALLING_MOTERESPONS) {
            InnkallingMoterespons(
                temaKode = temaKode,
                tekstNotatInnhold = message,
                dokIdNotat = documentId,
                foresporsel = null,
            )
        } else {
            null
        },
        foresporselFraSaksbehandlerForesporselSvar = if (dialogmeldingType == DialogmeldingType.DIALOGMELDING_FORESPORSEL_FRA_SAKSBEHANDLER_FORESPORSEL_SVAR) {
            ForesporselFraSaksbehandlerForesporselSvar(
                temaKode = temaKode,
                tekstNotatInnhold = message ?: "",
                dokIdNotat = documentId,
                datoNotat = null,
            )
        } else {
            null
        },
        henvendelseFraLegeHenvendelse = if (dialogmeldingType == DialogmeldingType.DIALOGMELDING_HENVENDELSE_FRA_LEGE_HENDVENDELSE) {
            HenvendelseFraLegeHenvendelse(
                temaKode = temaKode,
                tekstNotatInnhold = message ?: "",
                dokIdNotat = documentId,
                foresporsel = null,
                rollerRelatertNotat = null,
            )
        } else {
            null
        },
        // Navnet hentes fra syfohelsenettproxy ved prosessering.
        navnHelsepersonell = "",
        signaturDato = signature.signedAt.toLocalDateTimeOslo(),
    )

    return ReceivedDialogmelding(
        dialogmelding = dialogmelding,
        dialogmeldingType = dialogmeldingType,
        personNrPasient = patientIdent,
        personNrLege = signature.signingProviderIdent,
        navLogId = id,
        msgId = id,
        msgType = type.messageType.toMsgType(),
        conversationRef = conversationReference?.conversationId,
        parentRef = conversationReference?.parentMessageId,
        legekontorOrgNr = provider.office.orgNumber,
        legekontorHerId = provider.office.herId,
        legekontorOrgName = provider.office.orgName,
        mottattDato = receivedAt.toLocalDateTimeOslo(),
        source = DialogmeldingSource.HELSEMELDING,
        fellesformat = null,
        kafkaMelding = kafkaMelding,
    )
}

// Samme verdier som msgHead.msgInfo.type.v i XML-flyten.
private fun IncomingType.toMsgType(): String =
    when (this) {
        IncomingType.DIALOG_NOTE -> "DIALOG_NOTAT"
        IncomingType.DIALOG_RESPONSE -> "DIALOG_SVAR"
    }

private fun String.toLocalDateTimeOslo(): LocalDateTime =
    OffsetDateTime.parse(this)
        .atZoneSameInstant(ZoneId.of("Europe/Oslo"))
        .toLocalDateTime()
