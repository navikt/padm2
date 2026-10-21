package no.nav.syfo.client.pdl

import no.nav.syfo.model.PersonNavn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PdlNavnTest {

    @Test
    fun `Formaterer navn fra PDL med stor forbokstav`() {
        val personNavn = PdlNavn(
            fornavn = "OLA-PER",
            mellomnavn = "VAN DER",
            etternavn = "ØSTERÅS",
        ).toPersonNavn()

        assertEquals(PersonNavn(fornavn = "Ola-Per", mellomnavn = "Van Der", etternavn = "Østerås"), personNavn)
    }

    @Test
    fun `Blank mellomnavn blir null`() {
        val personNavn = PdlNavn(fornavn = "KARI", mellomnavn = " ", etternavn = "NORDMANN").toPersonNavn()

        assertNull(personNavn.mellomnavn)
        assertEquals("Nordmann, Kari", personNavn.etternavnFoerst())
    }

    @Test
    fun `Etternavn foerst med mellomnavn`() {
        val personNavn = PersonNavn(fornavn = "Kari", mellomnavn = "Marie", etternavn = "Nordmann")

        assertEquals("Nordmann, Kari Marie", personNavn.etternavnFoerst())
    }
}
