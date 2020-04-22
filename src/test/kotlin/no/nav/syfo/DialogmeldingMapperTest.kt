package no.nav.syfo

import java.io.StringReader
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.syfo.util.extractDialogmelding
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.getFileAsString
import org.junit.Test

internal class DialogmeldingMapperTest {

    @Test
    internal fun `Tester mapping fra fellesformat til Legeerklaring format`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_svar.xml"))
        ) as XMLEIFellesformat

        val dialomeldingxml = extractDialogmelding(felleformatDm)
    }
}
