package no.nav.syfo

import java.io.StringReader
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.createArenaDialogNotat
import no.nav.syfo.model.DialogmeldingKodeverk
import no.nav.syfo.model.findDialogmeldingType
import no.nav.syfo.model.toDialogmelding
import no.nav.syfo.util.extractDialogmelding
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.util.getFileAsStringISO88591
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldEqual
import org.junit.Test

internal class CreateArenaDialogNotatTest {

    @Test
    internal fun `Tester mapping fra fellesformat til ArenaDialogNotat svar foresporsel pasient`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_svar_foresporsel_om_pasient.xml"))
        ) as XMLEIFellesformat

        val msgHead: XMLMsgHead = felleformatDm.get()
        val receiverBlock = felleformatDm.get<XMLMottakenhetBlokk>()
        val personNumberPatient = msgHead.msgInfo.patient.ident.find { it.typeId.v == "FNR" }?.id ?: ""
        val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur
        val tssid = "1321415"

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"

        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )

        val arenaDialogNotat = createArenaDialogNotat(
            felleformatDm,
            tssid,
            personNumberDoctor,
            personNumberPatient,
            msgHead,
            receiverBlock,
            dialogmelding
        )

        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentType shouldBeEqualTo "DM"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentTypeVersjon shouldBeEqualTo "1.0"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentNavn shouldBeEqualTo "Svar på forespørsel"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentreferanse shouldBeEqualTo "b62016eb-6c2d-417a-8ecc-157b3c5ee2ca"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.ediLoggId shouldBeEqualTo "FiktivTestdata0001"
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeFnr shouldBeEqualTo "21312341414"
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.tssId.shouldEqual(tssid.toBigInteger())
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.fornavn shouldBeEqualTo "Inga"
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.etternavn shouldBeEqualTo "Valda"
        arenaDialogNotat.pasientData.person.personFnr shouldBeEqualTo "3143242342"
        arenaDialogNotat.pasientData.person.personNavn.fornavn shouldBeEqualTo "Etternavn"
        arenaDialogNotat.pasientData.person.personNavn.mellomnavn shouldBeEqualTo ""
        arenaDialogNotat.pasientData.person.personNavn.etternavn shouldBeEqualTo "Test"
        arenaDialogNotat.notatKategori shouldBeEqualTo "2"
        arenaDialogNotat.notatKode shouldBeEqualTo "25"
        arenaDialogNotat.notatTittel shouldBeEqualTo DialogmeldingKodeverk.OVERFORING_EPJ_INFORMASJON_SVAR_PAA_FORESPORSEL_OM_PASIENT.arenaNotatTittel
        arenaDialogNotat.notatTekst shouldBeEqualTo "Pasieten har masse info her"
        arenaDialogNotat.svarReferanse shouldBeEqualTo "OD1812186729156"
    }

    @Test
    internal fun `Tester mapping fra fellesformat til ArenaDialogNotat svar innkalling dialogmote`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_svar_innkalling_dialogmote.xml"))
        ) as XMLEIFellesformat

        val msgHead: XMLMsgHead = felleformatDm.get()
        val receiverBlock = felleformatDm.get<XMLMottakenhetBlokk>()
        val personNumberPatient = msgHead.msgInfo.patient.ident.find { it.typeId.v == "FNR" }?.id ?: ""
        val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur
        val tssid = "1321415"

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"

        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )

        val arenaDialogNotat = createArenaDialogNotat(
            felleformatDm,
            tssid,
            personNumberDoctor,
            personNumberPatient,
            msgHead,
            receiverBlock,
            dialogmelding
        )

        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentType shouldBeEqualTo "DM"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentTypeVersjon shouldBeEqualTo "1.0"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentNavn shouldBeEqualTo "Svar p.. foresp..rsel"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentreferanse shouldBeEqualTo "9be88bc5-4219-473e-954b-c0dd115ff4e0"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.ediLoggId shouldBeEqualTo "1901162204amsa22108.1"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentDato.year shouldEqual 2020
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentDato.monthValue shouldEqual 9
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentDato.dayOfMonth shouldEqual 21
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeFnr shouldBeEqualTo "12312414234"
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.tssId.shouldEqual(tssid.toBigInteger())
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.fornavn shouldBeEqualTo "Inga"
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.etternavn shouldBeEqualTo "Valda"
        arenaDialogNotat.pasientData.person.personFnr shouldBeEqualTo "3143242342"
        arenaDialogNotat.pasientData.person.personNavn.fornavn shouldBeEqualTo "Etternavn"
        arenaDialogNotat.pasientData.person.personNavn.mellomnavn shouldBeEqualTo ""
        arenaDialogNotat.pasientData.person.personNavn.etternavn shouldBeEqualTo "Test"
        arenaDialogNotat.notatKategori shouldBeEqualTo "1"
        arenaDialogNotat.notatKode shouldBeEqualTo "11"
        arenaDialogNotat.notatTittel shouldBeEqualTo DialogmeldingKodeverk.SVAR_PAA_INNKALLING_DIALOGMOTE_JA_JEG_KOMMER.arenaNotatTittel
        arenaDialogNotat.notatTekst shouldBeEqualTo "Ta gjerne kontakt ang hvilket telefonnummer jeg skal ringe. Mvh Inga F. Valda."
        arenaDialogNotat.svarReferanse shouldBeEqualTo "OD2009169905747"
        arenaDialogNotat.notatDato.year shouldEqual 2020
        arenaDialogNotat.notatDato.monthValue shouldEqual 9
        arenaDialogNotat.notatDato.dayOfMonth shouldEqual 21
    }

    @Test
    internal fun `Tester mapping fra fellesformat til ArenaDialogNotat notat`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat.xml"))
        ) as XMLEIFellesformat

        val msgHead: XMLMsgHead = felleformatDm.get()
        val receiverBlock = felleformatDm.get<XMLMottakenhetBlokk>()
        val personNumberPatient = msgHead.msgInfo.patient.ident.find { it.typeId.v == "FNR" }?.id ?: ""
        val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur
        val tssid = "1321415"

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"

        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )

        val arenaDialogNotat = createArenaDialogNotat(
            felleformatDm,
            tssid,
            personNumberDoctor,
            personNumberPatient,
            msgHead,
            receiverBlock,
            dialogmelding
        )

        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentType shouldBeEqualTo "DM"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentTypeVersjon shouldBeEqualTo "1.0"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentNavn shouldBeEqualTo "Notat"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentreferanse shouldBeEqualTo "37340D30-FE14-42B5-985F-A8FF8FFA0CB5"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.ediLoggId shouldBeEqualTo "1901162157lege21826.1"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentDato.shouldEqual(LocalDateTime.of(2019, 1, 16, 21, 51, 35, 531000000))
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeFnr shouldBeEqualTo "1231124124"
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.tssId.shouldEqual(tssid.toBigInteger())
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.fornavn shouldBeEqualTo "Inga"
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.etternavn shouldBeEqualTo "Valda"
        arenaDialogNotat.pasientData.person.personFnr shouldBeEqualTo "3143242342"
        arenaDialogNotat.pasientData.person.personNavn.fornavn shouldBeEqualTo "Etternavn"
        arenaDialogNotat.pasientData.person.personNavn.mellomnavn shouldBeEqualTo ""
        arenaDialogNotat.pasientData.person.personNavn.etternavn shouldBeEqualTo "Test"
        arenaDialogNotat.notatKategori shouldBeEqualTo "3"
        arenaDialogNotat.notatKode shouldBeEqualTo "31"
        arenaDialogNotat.notatTittel shouldBeEqualTo DialogmeldingKodeverk.HENVENDELSE_OM_PASIENT_HENVENDELSE_OM_SYKEFRAVARSOPPFOLGING.arenaNotatTittel
        arenaDialogNotat.notatTekst shouldBeEqualTo "Hei,Det gjelder pas. Sender som vedlegg epikrisen"
        arenaDialogNotat.svarReferanse shouldBeEqualTo "A1578B81-0042-453B-8527-6CF182BDA6C7"
        arenaDialogNotat.notatDato.shouldEqual(LocalDateTime.of(2019, 1, 16, 21, 51, 35, 531000000))
    }

    @Test
    internal fun `Tester mapping av arena biten`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml"))
        ) as XMLEIFellesformat

        val msgHead: XMLMsgHead = felleformatDm.get()
        val receiverBlock = felleformatDm.get<XMLMottakenhetBlokk>()
        val personNumberPatient = msgHead.msgInfo.patient.ident.find { it.typeId.v == "FNR" }?.id ?: ""
        val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur
        val tssid = "1321415"

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"

        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )

        val arenaDialogNotat = createArenaDialogNotat(
            felleformatDm,
            tssid,
            personNumberDoctor,
            personNumberPatient,
            msgHead,
            receiverBlock,
            dialogmelding
        )

        arenaDialogNotat.notatKategori shouldBeEqualTo "3"
        arenaDialogNotat.notatKode shouldBeEqualTo "31"
        arenaDialogNotat.notatTittel shouldBeEqualTo DialogmeldingKodeverk.HENVENDELSE_OM_PASIENT_HENVENDELSE_OM_SYKEFRAVARSOPPFOLGING.arenaNotatTittel
    }

    @Test
    internal fun `Tester mapping av arena tekstNotatInnhold`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml"))
        ) as XMLEIFellesformat

        val msgHead: XMLMsgHead = felleformatDm.get()
        val receiverBlock = felleformatDm.get<XMLMottakenhetBlokk>()
        val personNumberPatient = msgHead.msgInfo.patient.ident.find { it.typeId.v == "FNR" }?.id ?: ""
        val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur
        val tssid = "1321415"

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"

        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )

        val arenaDialogNotat = createArenaDialogNotat(
            felleformatDm,
            tssid,
            personNumberDoctor,
            personNumberPatient,
            msgHead,
            receiverBlock,
            dialogmelding
        )

        arenaDialogNotat.notatTekst shouldBeEqualTo "Hei,Det gjelder pas. Sender sm2013 som vedlegg"
    }
}
