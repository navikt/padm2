package no.nav.syfo

import java.io.StringReader
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.syfo.model.findDialogmeldingType
import no.nav.syfo.util.extractDialogmelding
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.validation.isKodeverkValid
import org.amshove.kluent.shouldBe
import org.junit.Test

internal class ValidateDialogMeldingKodeverkTest {

    @Test
    internal fun `Should be invalid dialogmeldingkodeverk combo`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_notat_invalid.xml"))
        ) as XMLEIFellesformat

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val receiverBlock = felleformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

        val validateDialogMeldingKodeverk = isKodeverkValid(dialomeldingxml, dialogmeldingType)

        validateDialogMeldingKodeverk shouldBe false
    }

    @Test
    internal fun `Should be valid dialogmeldingkodeverk combo`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml"))
        ) as XMLEIFellesformat

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val receiverBlock = felleformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

        val validateDialogMeldingKodeverk = isKodeverkValid(dialomeldingxml, dialogmeldingType)

        validateDialogMeldingKodeverk shouldBe true
    }

    @Test
    internal fun `Should be invalid if temakodet is not found in Kodeverklist`() {
        val fellesformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml"))
        ) as XMLEIFellesformat

        val dialogmeldingxml = extractDialogmelding(fellesformatDm)
        dialogmeldingxml.notat.first().temaKodet.v = "INVALID"

        val receiverBlock = fellesformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

        val validateDialogMeldingKodeverk = isKodeverkValid(dialogmeldingxml, dialogmeldingType)

        validateDialogMeldingKodeverk shouldBe false
    }
}
