package no.nav.syfo.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BehandlerTest {

    private val fallback = "fallback"

    private fun behandler(fornavn: String?, mellomnavn: String?, etternavn: String?) =
        Behandler(fnr = "01010112377", hprId = null, fornavn = fornavn, mellomnavn = mellomnavn, etternavn = etternavn)

    @Test
    fun `getNavn formats fornavn etternavn`() {
        assertEquals("Lego Legesen", behandler("Lego", null, "Legesen").getNavn(fallback = fallback))
    }

    @Test
    fun `getNavn formats fornavn mellomnavn etternavn`() {
        assertEquals("Leg E. Legesen", behandler("Leg", "E.", "Legesen").getNavn(fallback = fallback))
    }

    @Test
    fun `getNavn with etternavnFoerst formats etternavn, fornavn`() {
        assertEquals("Legesen, Lego", behandler("Lego", null, "Legesen").getNavn(etternavnFoerst = true, fallback = fallback))
    }

    @Test
    fun `getNavn with etternavnFoerst formats etternavn, fornavn mellomnavn`() {
        assertEquals("Legesen, Leg E.", behandler("Leg", "E.", "Legesen").getNavn(etternavnFoerst = true, fallback = fallback))
    }

    @Test
    fun `getNavn does not print null or blank for missing fornavn`() {
        assertEquals("Legesen", behandler(null, null, "Legesen").getNavn(fallback = fallback))
        assertEquals("Legesen", behandler("", null, "Legesen").getNavn(etternavnFoerst = true, fallback = fallback))
        assertEquals("E. Legesen", behandler(null, "E.", "Legesen").getNavn(fallback = fallback))
    }

    @Test
    fun `getNavn uses fornavn when etternavn is missing`() {
        assertEquals("Lego", behandler("Lego", null, null).getNavn(etternavnFoerst = true, fallback = fallback))
    }

    @Test
    fun `getNavn uses fallback, not fnr, when no names`() {
        assertEquals(fallback, behandler(null, null, null).getNavn(fallback = fallback))
        assertEquals(fallback, behandler("", "", "").getNavn(etternavnFoerst = true, fallback = fallback))
    }
}
