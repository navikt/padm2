package no.nav.syfo

import java.io.StringReader
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.syfo.model.DialogmeldingType
import no.nav.syfo.model.toDialogmelding
import no.nav.syfo.util.extractDialogmelding
import no.nav.syfo.util.extractVedlegg
import no.nav.syfo.util.fellesformatUnmarshaller
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

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = DialogmeldingType.DIALOGMELDING_SVAR_INNKALLING_DIALOGMOTE,
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

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = DialogmeldingType.DIALOGMELDING_SVAR_FORESPORSEL_OM_PASIENT,
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

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = DialogmeldingType.DIALOGMELDING_HENVENDELSE_FRA_LEGE,
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

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = DialogmeldingType.DIALOGMELDING_HENVENDELSE_FRA_LEGE,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )
        val extractVedlegg = extractVedlegg(felleformatDm)

        dialogmelding.id shouldBeEqualTo dialogmeldingId
        extractVedlegg.size.shouldBe(2)
    }
}
