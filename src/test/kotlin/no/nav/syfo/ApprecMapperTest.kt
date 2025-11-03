package no.nav.syfo

import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.createApprec
import no.nav.syfo.util.get
import no.nav.syfo.util.getFileAsStringISO88591
import no.nav.syfo.util.safeUnmarshal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ApprecMapperTest {
    @Test
    internal fun `Tester mapping fra fellesformat til apprec svar foresporsel om pasient`() {
        val felleformatDm = safeUnmarshal(
            getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_svar_foresporsel_om_pasient.xml")
        )

        val apprec = createApprec(felleformatDm, ApprecStatus.OK)
        val apprecEmottakblokk = apprec.get<XMLMottakenhetBlokk>()

        assertEquals("Bekreftelse", apprecEmottakblokk.ebAction)
        assertEquals("Saksbehandler", apprecEmottakblokk.ebRole)
        assertEquals("ForesporselFraSaksbehandler", apprecEmottakblokk.ebService)
    }
}
