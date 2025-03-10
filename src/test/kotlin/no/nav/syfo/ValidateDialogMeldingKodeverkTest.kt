package no.nav.syfo

import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.syfo.model.findDialogmeldingType
import no.nav.syfo.util.extractDialogmelding
import no.nav.syfo.util.get
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.util.safeUnmarshal
import no.nav.syfo.validation.isKodeverkValid
import org.amshove.kluent.shouldBe
import org.junit.jupiter.api.Test
import java.util.*

internal class ValidateDialogMeldingKodeverkTest {

    val msgId = UUID.randomUUID().toString()

    @Test
    internal fun `Should be invalid dialogmeldingkodeverk combo`() {
        val felleformatDm = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_dialog_notat_invalid.xml")
        )

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val emottakblokk = felleformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)

        val validateDialogMeldingKodeverk = isKodeverkValid(msgId, dialomeldingxml, dialogmeldingType)

        validateDialogMeldingKodeverk shouldBe false
    }

    @Test
    internal fun `Should be valid dialogmeldingkodeverk combo`() {
        val felleformatDm = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
        )

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val emottakblokk = felleformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)

        val validateDialogMeldingKodeverk = isKodeverkValid(msgId, dialomeldingxml, dialogmeldingType)

        validateDialogMeldingKodeverk shouldBe true
    }

    @Test
    internal fun `Should be invalid if temakodet is not found in Kodeverklist`() {
        val fellesformatDm = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
        )

        val dialogmeldingxml = extractDialogmelding(fellesformatDm)
        dialogmeldingxml.notat.first().temaKodet.v = "INVALID"

        val emottakblokk = fellesformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)

        val validateDialogMeldingKodeverk = isKodeverkValid(msgId, dialogmeldingxml, dialogmeldingType)

        validateDialogMeldingKodeverk shouldBe false
    }
}
