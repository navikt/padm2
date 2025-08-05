package no.nav.syfo.domain

import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.util.safeUnmarshal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals

class ReceivedDialogmeldingTest {

    private val fellesformatDialognotat =
        getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
    private val fellesformatDialognotatXml = safeUnmarshal(fellesformatDialognotat)

    @Test
    fun `should create a valid Virksomhetsnummer`() {
        val receivedDialogmelding =
            ReceivedDialogmelding.create(
                dialogmeldingId = UUID.randomUUID().toString(),
                fellesformat = fellesformatDialognotatXml,
                inputMessageText = fellesformatDialognotat
            )
        assertEquals("223456789", receivedDialogmelding.legekontorOrgNr?.value)
    }

    @Test
    fun `should fail with invalid Virksomhetsnummer`() {
        val fellesformatDialognotatIkkeGyldig =
            getFileAsString("src/test/resources/dialogmelding_dialog_notat_ikke_gyldig_orgnr.xml")
        val fellesformatDialognotatXmlIkkeGyldig = safeUnmarshal(fellesformatDialognotatIkkeGyldig)
        val exception = assertThrows<IllegalArgumentException> {
            ReceivedDialogmelding.create(
                dialogmeldingId = UUID.randomUUID().toString(),
                fellesformat = fellesformatDialognotatXmlIkkeGyldig,
                inputMessageText = fellesformatDialognotatIkkeGyldig
            )
        }
        assertEquals("LegekontorOrgNummer must be a 9-digit number", exception.message)
    }
}
