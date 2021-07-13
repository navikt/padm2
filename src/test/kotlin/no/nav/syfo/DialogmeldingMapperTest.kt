package no.nav.syfo

import java.io.StringReader
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.syfo.model.*
import no.nav.syfo.util.extractDialogmelding
import no.nav.syfo.util.extractVedlegg
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.getFileAsString
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

internal class DialogmeldingMapperTest {

    @Test
    internal fun `Tester mapping fra fellesformat til dialogmelding svar innkalling dialogmote`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_svar_innkalling_dialogmote.xml"))
        ) as XMLEIFellesformat

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"
        val receiverBlock = felleformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )

        dialogmelding.id shouldBeEqualTo dialogmeldingId
    }

    @Test
    internal fun `Tester mapping fra fellesformat til dialogmelding  svar foresporsel om pasient`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_svar_foresporsel_om_pasient.xml"))
        ) as XMLEIFellesformat

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"
        val receiverBlock = felleformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )

        dialogmelding.id shouldBeEqualTo dialogmeldingId
    }

    @Test
    internal fun `Tester mapping fra fellesformat til dialogmelding notat`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml"))
        ) as XMLEIFellesformat

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"
        val receiverBlock = felleformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )

        dialogmelding.id shouldBeEqualTo dialogmeldingId
    }

    @Test
    internal fun `Tester mapping fra fellesformat til dialogmelding notat med vedlegg`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml"))
        ) as XMLEIFellesformat

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"
        val receiverBlock = felleformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )
        val extractVedlegg = extractVedlegg(felleformatDm)
        val vedleggListe = extractVedlegg.map { it.toVedlegg() }

        dialogmelding.id shouldBeEqualTo dialogmeldingId
        vedleggListe.size.shouldBe(2)
        vedleggListe[0].beskrivelse shouldBeEqualTo "Et vedlegg fra lege"
        vedleggListe[1].beskrivelse shouldBeEqualTo "Et bilde fra lege"
    }

    @Test
    internal fun `Tester mapping fra fellesformat til dialogmelding notat med vedlegg der description mangler`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_notat_vedlegg_missing_description.xml"))
        ) as XMLEIFellesformat

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"
        val receiverBlock = felleformatDm.get<XMLMottakenhetBlokk>()

        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )
        val extractVedlegg = extractVedlegg(felleformatDm)
        val vedleggListe = extractVedlegg.map { it.toVedlegg() }

        dialogmelding.id shouldBeEqualTo dialogmeldingId
        vedleggListe.size.shouldBe(2)
        vedleggListe[0].beskrivelse shouldBeEqualTo ""
        vedleggListe[1].beskrivelse shouldBeEqualTo "Et bilde fra lege"
    }
}
