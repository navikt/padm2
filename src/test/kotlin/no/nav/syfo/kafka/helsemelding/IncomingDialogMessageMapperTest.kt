package no.nav.syfo.kafka.helsemelding

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import no.nav.syfo.model.DialogmeldingSource
import no.nav.syfo.model.DialogmeldingType
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.TemaKode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class IncomingDialogMessageMapperTest {

    private val dialogmeldingId = "c2b7a8f1-5d3e-4c6b-9a0f-1e2d3c4b5a69"

    private fun map(json: String) = receivedDialogmeldingFromHelsemelding(
        dialogmeldingId = dialogmeldingId,
        kafkaMelding = json,
    )

    private data class Forventet(
        val type: IncomingDialogMessageType,
        val kodeverkOID: String,
        val v: String,
        val dialogmeldingType: DialogmeldingType,
        val msgType: String,
    )

    private val forventninger = listOf(
        Forventet(
            IncomingDialogMessageType.ACCEPTS_MEETING_INVITATION,
            "2.16.578.1.12.4.1.1.8126",
            "1",
            DialogmeldingType.DIALOGMELDING_DIALOGMOTE_INNKALLING_MOTERESPONS,
            "DIALOG_SVAR",
        ),
        Forventet(
            IncomingDialogMessageType.REQUESTS_NEW_MEETING_TIME,
            "2.16.578.1.12.4.1.1.8126",
            "2",
            DialogmeldingType.DIALOGMELDING_DIALOGMOTE_INNKALLING_MOTERESPONS,
            "DIALOG_SVAR",
        ),
        Forventet(
            IncomingDialogMessageType.DECLINES_MEETING_WITH_REASON,
            "2.16.578.1.12.4.1.1.8126",
            "3",
            DialogmeldingType.DIALOGMELDING_DIALOGMOTE_INNKALLING_MOTERESPONS,
            "DIALOG_SVAR",
        ),
        Forventet(
            IncomingDialogMessageType.PATIENT_REQUEST_RESPONSE,
            "2.16.578.1.12.4.1.1.9069",
            "5",
            DialogmeldingType.DIALOGMELDING_FORESPORSEL_FRA_SAKSBEHANDLER_FORESPORSEL_SVAR,
            "DIALOG_SVAR",
        ),
        Forventet(
            IncomingDialogMessageType.SICK_LEAVE_FOLLOW_UP_INQUIRY,
            "2.16.578.1.12.4.1.1.8128",
            "1",
            DialogmeldingType.DIALOGMELDING_HENVENDELSE_FRA_LEGE_HENDVENDELSE,
            "DIALOG_NOTAT",
        ),
        Forventet(
            IncomingDialogMessageType.PATIENT_INQUIRY,
            "2.16.578.1.12.4.1.1.8128",
            "2",
            DialogmeldingType.DIALOGMELDING_HENVENDELSE_FRA_LEGE_HENDVENDELSE,
            "DIALOG_NOTAT",
        ),
    )

    @Test
    fun `covers all incoming dialog message types`() {
        assertEquals(IncomingDialogMessageType.entries.toSet(), forventninger.map { it.type }.toSet())
    }

    @Test
    fun `maps type to kodeverk, dialogmeldingType and msgType`() {
        forventninger.forEach { forventet ->
            val received = map(generateIncomingDialogMessageJson(type = forventet.type))
            val temaKode = received.temaKode()

            assertEquals(forventet.kodeverkOID, temaKode.kodeverkOID, "kodeverkOID for ${forventet.type}")
            assertEquals(forventet.v, temaKode.v, "v for ${forventet.type}")
            assertEquals(forventet.dialogmeldingType, received.dialogmeldingType, "dialogmeldingType for ${forventet.type}")
            assertEquals(forventet.msgType, received.msgType, "msgType for ${forventet.type}")
        }
    }

    @Test
    fun `sets exactly one dialogmelding variant matching the type`() {
        val moterespons = map(generateIncomingDialogMessageJson(type = IncomingDialogMessageType.ACCEPTS_MEETING_INVITATION)).dialogmelding
        assertNotNull(moterespons.innkallingMoterespons)
        assertNull(moterespons.foresporselFraSaksbehandlerForesporselSvar)
        assertNull(moterespons.henvendelseFraLegeHenvendelse)

        val svar = map(generateIncomingDialogMessageJson(type = IncomingDialogMessageType.PATIENT_REQUEST_RESPONSE)).dialogmelding
        assertNull(svar.innkallingMoterespons)
        assertNotNull(svar.foresporselFraSaksbehandlerForesporselSvar)
        assertNull(svar.henvendelseFraLegeHenvendelse)

        val henvendelse = map(generateIncomingDialogMessageJson(type = IncomingDialogMessageType.PATIENT_INQUIRY)).dialogmelding
        assertNull(henvendelse.innkallingMoterespons)
        assertNull(henvendelse.foresporselFraSaksbehandlerForesporselSvar)
        assertNotNull(henvendelse.henvendelseFraLegeHenvendelse)
    }

    @Test
    fun `maps message fields`() {
        val json = generateIncomingDialogMessageJson(
            id = "msg-id",
            type = IncomingDialogMessageType.PATIENT_REQUEST_RESPONSE,
            message = "Svar paa forespoersel",
        )
        val received = map(json)

        assertEquals(dialogmeldingId, received.dialogmelding.id)
        assertEquals("msg-id", received.msgId)
        assertEquals("msg-id", received.navLogId)
        assertEquals("01010112345", received.personNrPasient)
        assertEquals("03030312345", received.personNrLege)
        assertEquals("conversation-id", received.conversationRef)
        assertEquals("parent-message-id", received.parentRef)
        assertEquals("223456789", received.legekontorOrgNr)
        assertEquals("1234567", received.legekontorHerId)
        assertEquals("Kule helsetjenester AS", received.legekontorOrgName)
        assertEquals(LocalDateTime.of(2026, 6, 3, 14, 0, 0), received.mottattDato)
        assertEquals(LocalDateTime.of(2026, 6, 3, 13, 59, 0), received.dialogmelding.signaturDato)
        assertEquals("", received.dialogmelding.navnHelsepersonell)
        assertEquals(DialogmeldingSource.HELSEMELDING, received.source)
        assertNull(received.fellesformat)
        assertEquals(json, received.kafkaMelding)

        val svar = received.dialogmelding.foresporselFraSaksbehandlerForesporselSvar!!
        assertEquals("Svar paa forespoersel", svar.tekstNotatInnhold)
        assertEquals("OD2503016631907", svar.dokIdNotat)
    }

    @Test
    fun `handles missing conversationReference and message`() {
        val received = map(
            generateIncomingDialogMessageJson(
                type = IncomingDialogMessageType.PATIENT_INQUIRY,
                message = null,
                conversationReference = false,
            )
        )

        assertNull(received.conversationRef)
        assertNull(received.parentRef)
        assertEquals("", received.dialogmelding.henvendelseFraLegeHenvendelse!!.tekstNotatInnhold)
    }

    @Test
    fun `ignores unknown fields`() {
        val json = generateIncomingDialogMessageJson().replaceFirst("{", """{"nyttFelt": "verdi",""")

        assertEquals(DialogmeldingSource.HELSEMELDING, map(json).source)
    }

    @Test
    fun `fails on unsupported schema version`() {
        assertThrows<IllegalArgumentException> {
            map(generateIncomingDialogMessageJson(version = 2))
        }
    }

    @Test
    fun `fails on missing required field`() {
        val json = generateIncomingDialogMessageJson().replace(""""documentId": "OD2503016631907"""", """"documentId": null""")

        assertThrows<MismatchedInputException> {
            map(json)
        }
    }

    private fun ReceivedDialogmelding.temaKode(): TemaKode =
        dialogmelding.innkallingMoterespons?.temaKode
            ?: dialogmelding.foresporselFraSaksbehandlerForesporselSvar?.temaKode
            ?: dialogmelding.henvendelseFraLegeHenvendelse!!.temaKode
}
