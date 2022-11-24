package no.nav.syfo

import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.syfo.util.extractIdentFromBehandler
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.getFileAsString
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import java.io.StringReader

class ExtractFromFellesFormatTest {
    private val BEHANDLER_FNR = "01010112377"

    @Test
    internal fun `Extract behandlerident`() {
        val fellesformat = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml"))
        ) as XMLEIFellesformat

        val behandlerIdent = extractIdentFromBehandler(fellesformat)

        behandlerIdent shouldBeEqualTo BEHANDLER_FNR
    }

    @Test
    internal fun `Extract behandlerident and return null if it doesn't exist`() {
        val fellesformat = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_svar_foresporsel_om_pasient.xml"))
        ) as XMLEIFellesformat

        val behandlerIdent = extractIdentFromBehandler(fellesformat)

        behandlerIdent shouldBeEqualTo null
    }

    @Test
    internal fun `Does not find behandlerident in RollerRelatertNotat`() {
        // We might want to support this at some point
        val fellesformat = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_kiropraktor.xml"))
        ) as XMLEIFellesformat

        val behandlerIdent = extractIdentFromBehandler(fellesformat)

        behandlerIdent shouldBeEqualTo null
    }
}
