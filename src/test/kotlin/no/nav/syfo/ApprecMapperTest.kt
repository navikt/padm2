package no.nav.syfo

import java.io.StringReader
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.createApprec
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.getFileAsStringISO88591
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

internal class ApprecMapperTest {
    @Test
    internal fun `Tester mapping fra fellesformat til apprec svar foresporsel om pasient`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_svar_foresporsel_om_pasient.xml"))
        ) as XMLEIFellesformat

        val apprec = createApprec(felleformatDm, ApprecStatus.OK)
        val apprecReceiverBlock = apprec.get<XMLMottakenhetBlokk>()

        apprecReceiverBlock.ebAction shouldBeEqualTo "Bekreftelse"
        apprecReceiverBlock.ebRole shouldBeEqualTo "Saksbehandler"
        apprecReceiverBlock.ebService shouldBeEqualTo "ForesporselFraSaksbehandler"
    }
}
