package no.nav.syfo

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.syfo.model.*
import no.nav.syfo.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DialogmeldingMapperTest {

    @Test
    internal fun `Tester mapping fra fellesformat til dialogmelding svar innkalling dialogmote`() {
        val felleformatDm = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_dialog_svar_innkalling_dialogmote.xml")
        )

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"
        val emottakblokk = felleformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )

        assertEquals(dialogmeldingId, dialogmelding.id)
    }

    @Test
    internal fun `Tester mapping fra fellesformat til dialogmelding  svar foresporsel om pasient`() {
        val felleformatDm = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_dialog_svar_foresporsel_om_pasient.xml")
        )

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"
        val emottakblokk = felleformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )

        assertEquals(dialogmeldingId, dialogmelding.id)
    }

    @Test
    internal fun `Tester mapping fra fellesformat til dialogmelding notat`() {
        val felleformatDm = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
        )

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"
        val emottakblokk = felleformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )

        assertEquals(dialogmeldingId, dialogmelding.id)
    }

    @Test
    internal fun `Tester mapping fra fellesformat til dialogmelding notat med vedlegg`() {
        val felleformatDm = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
        )

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"
        val emottakblokk = felleformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )
        val extractVedlegg = extractValidVedlegg(felleformatDm)
        val vedleggListe = extractVedlegg.map { it.toVedlegg() }

        assertEquals(dialogmeldingId, dialogmelding.id)
        assertEquals(2, vedleggListe.size)
        assertEquals("Et vedlegg fra lege", vedleggListe[0].beskrivelse)
        assertEquals("Et bilde fra lege", vedleggListe[1].beskrivelse)
    }

    @Test
    internal fun `Tester mapping fra fellesformat til dialogmelding notat med vedlegg der description mangler`() {
        val felleformatDm = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_dialog_notat_vedlegg_missing_description.xml")
        )

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"
        val emottakblokk = felleformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )
        val extractVedlegg = extractValidVedlegg(felleformatDm)
        val vedleggListe = extractVedlegg.map { it.toVedlegg() }

        assertEquals(dialogmeldingId, dialogmelding.id)
        assertEquals(2, vedleggListe.size)
        assertEquals("", vedleggListe[0].beskrivelse)
        assertEquals("Et bilde fra lege", vedleggListe[1].beskrivelse)
    }
}
