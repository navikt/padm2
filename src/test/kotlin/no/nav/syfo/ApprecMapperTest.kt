package no.nav.syfo

import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.createApprec
import no.nav.syfo.util.get
import no.nav.syfo.util.getFileAsStringISO88591
import no.nav.syfo.util.safeUnmarshal
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

internal class ApprecMapperTest {
    @Test
    internal fun `Tester mapping fra fellesformat til apprec svar foresporsel om pasient`() {
        val felleformatDm = safeUnmarshal(
            getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_svar_foresporsel_om_pasient.xml")
        )

        val apprec = createApprec(felleformatDm, ApprecStatus.OK)
        val apprecEmottakblokk = apprec.get<XMLMottakenhetBlokk>()

        apprecEmottakblokk.ebAction shouldBeEqualTo "Bekreftelse"
        apprecEmottakblokk.ebRole shouldBeEqualTo "Saksbehandler"
        apprecEmottakblokk.ebService shouldBeEqualTo "ForesporselFraSaksbehandler"
    }
}
