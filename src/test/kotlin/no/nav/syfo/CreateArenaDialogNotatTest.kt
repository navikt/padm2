package no.nav.syfo

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.createArenaDialogNotat
import no.nav.syfo.client.createAvsender
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.DialogmeldingKodeverk
import no.nav.syfo.model.findDialogmeldingType
import no.nav.syfo.model.toDialogmelding
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.math.BigInteger

internal class CreateArenaDialogNotatTest {

    private val FASTLEGE_FNR = "21312341414"

    @Test
    internal fun `Tester mapping fra fellesformat til ArenaDialogNotat svar foresporsel pasient`() {
        val felleformatDm = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_dialog_svar_foresporsel_om_pasient.xml")
        )

        val msgHead: XMLMsgHead = felleformatDm.get()
        val emottakblokk = felleformatDm.get<XMLMottakenhetBlokk>()
        val personNumberPatient = extractInnbyggerident(felleformatDm) ?: ""
        val personNumberDoctor = emottakblokk.avsenderFnrFraDigSignatur
        val tssid = "1321415"

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"

        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)

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
            emottakblokk,
            dialogmelding
        )

        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentType shouldBeEqualTo "DM"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentTypeVersjon shouldBeEqualTo "1.0"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentNavn shouldBeEqualTo "Svar på forespørsel"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentreferanse shouldBeEqualTo "b62016eb-6c2d-417a-8ecc-157b3c5ee2ca"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.ediLoggId shouldBeEqualTo "FiktivTestdata0001"
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeFnr shouldBeEqualTo FASTLEGE_FNR
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.tssId.shouldBeEqualTo(tssid.toBigInteger())
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
    internal fun `Default to 0 when tss id is empty string`() {
        val behandler = Behandler(etternavn = "Etternavn", fornavn = "Fornavn", mellomnavn = "Mellomnavn")
        val avsender = createAvsender(FASTLEGE_FNR, "", behandler)
        avsender.lege.legeFnr shouldBeEqualTo FASTLEGE_FNR
        avsender.lege.tssId shouldBeEqualTo BigInteger("0")
    }

    @Test
    internal fun `Default to 0 when tss id is null`() {
        val behandler = Behandler("Etternavn", "Fornavn", "Mellomnavn")
        val avsender = createAvsender(FASTLEGE_FNR, null, behandler)
        avsender.lege.legeFnr shouldBeEqualTo FASTLEGE_FNR
        avsender.lege.tssId shouldBeEqualTo BigInteger("0")
    }

    @Test
    internal fun `Tester mapping fra fellesformat til ArenaDialogNotat svar innkalling dialogmote`() {
        val felleformatDm = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_dialog_svar_innkalling_dialogmote.xml")
        )

        val msgHead: XMLMsgHead = felleformatDm.get()
        val emottakblokk = felleformatDm.get<XMLMottakenhetBlokk>()
        val personNumberPatient = extractInnbyggerident(felleformatDm) ?: ""
        val personNumberDoctor = emottakblokk.avsenderFnrFraDigSignatur
        val tssid = "1321415"

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"

        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)

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
            emottakblokk,
            dialogmelding
        )

        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentType shouldBeEqualTo "DM"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentTypeVersjon shouldBeEqualTo "1.0"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentNavn shouldBeEqualTo "Svar p.. foresp..rsel"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentreferanse shouldBeEqualTo "9be88bc5-4219-473e-954b-c0dd115ff4e0"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.ediLoggId shouldBeEqualTo "1901162204amsa22108.1"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentDato.year shouldBeEqualTo 2020
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentDato.monthValue shouldBeEqualTo 9
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentDato.dayOfMonth shouldBeEqualTo 21
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeFnr shouldBeEqualTo "12312414234"
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.tssId.shouldBeEqualTo(tssid.toBigInteger())
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
        arenaDialogNotat.notatDato.year shouldBeEqualTo 2020
        arenaDialogNotat.notatDato.monthValue shouldBeEqualTo 9
        arenaDialogNotat.notatDato.dayOfMonth shouldBeEqualTo 21
    }

    @Test
    internal fun `Tester mapping fra fellesformat til ArenaDialogNotat notat`() {
        val felleformatDm = safeUnmarshal(
            getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat.xml")
        )

        val msgHead: XMLMsgHead = felleformatDm.get()
        val emottakblokk = felleformatDm.get<XMLMottakenhetBlokk>()
        val personNumberPatient = extractInnbyggerident(felleformatDm) ?: ""
        val personNumberDoctor = emottakblokk.avsenderFnrFraDigSignatur
        val tssid = "1321415"

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"

        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )

        val localDateTime = LocalDateTime.parse("2019-01-16T22:51:35.5317672")
        val arenaDialogNotat = createArenaDialogNotat(
            felleformatDm,
            tssid,
            personNumberDoctor,
            personNumberPatient,
            msgHead,
            emottakblokk,
            dialogmelding
        )

        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentType shouldBeEqualTo "DM"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentTypeVersjon shouldBeEqualTo "1.0"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentNavn shouldBeEqualTo "Notat"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentreferanse shouldBeEqualTo "37340D30-FE14-42B5-985F-A8FF8FFA0CB5"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.ediLoggId shouldBeEqualTo "1901162157lege21826.1"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentDato.shouldBeEqualTo(localDateTime)
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeFnr shouldBeEqualTo "01010112377"
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.tssId.shouldBeEqualTo(tssid.toBigInteger())
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.fornavn shouldBeEqualTo "Inga"
        arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.etternavn shouldBeEqualTo "Valda"
        arenaDialogNotat.pasientData.person.personFnr shouldBeEqualTo "01010142365"
        arenaDialogNotat.pasientData.person.personNavn.fornavn shouldBeEqualTo "Etternavn"
        arenaDialogNotat.pasientData.person.personNavn.mellomnavn shouldBeEqualTo ""
        arenaDialogNotat.pasientData.person.personNavn.etternavn shouldBeEqualTo "Test"
        arenaDialogNotat.notatKategori shouldBeEqualTo "3"
        arenaDialogNotat.notatKode shouldBeEqualTo "31"
        arenaDialogNotat.notatTittel shouldBeEqualTo DialogmeldingKodeverk.HENVENDELSE_OM_PASIENT_HENVENDELSE_OM_SYKEFRAVARSOPPFOLGING.arenaNotatTittel
        arenaDialogNotat.notatTekst shouldBeEqualTo "Hei,Det gjelder pas. Sender som vedlegg epikrisen"
        arenaDialogNotat.svarReferanse shouldBeEqualTo "A1578B81-0042-453B-8527-6CF182BDA6C7"
        arenaDialogNotat.notatDato.shouldBeEqualTo(localDateTime)
    }

    @Test
    internal fun `Tester mapping fra fellesformat til ArenaDialogNotat notat, med dnr for pasient i stedet for fnr`() {
        val felleformatDm = safeUnmarshal(
            getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat_dnr.xml")
        )

        val msgHead: XMLMsgHead = felleformatDm.get()
        val emottakblokk = felleformatDm.get<XMLMottakenhetBlokk>()
        val personNumberPatient = extractInnbyggerident(felleformatDm) ?: ""
        val personNumberDoctor = emottakblokk.avsenderFnrFraDigSignatur
        val tssid = "1321415"

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"

        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)

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
            emottakblokk,
            dialogmelding
        )

        arenaDialogNotat.pasientData.person.personFnr shouldBeEqualTo "45088649080"
    }

    @Test
    internal fun `Tester mapping av arena biten`() {
        val felleformatDm = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
        )

        val msgHead: XMLMsgHead = felleformatDm.get()
        val emottakblokk = felleformatDm.get<XMLMottakenhetBlokk>()
        val personNumberPatient = extractInnbyggerident(felleformatDm) ?: ""
        val personNumberDoctor = emottakblokk.avsenderFnrFraDigSignatur
        val tssid = "1321415"

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"

        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)

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
            emottakblokk,
            dialogmelding
        )

        arenaDialogNotat.notatKategori shouldBeEqualTo "3"
        arenaDialogNotat.notatKode shouldBeEqualTo "31"
        arenaDialogNotat.notatTittel shouldBeEqualTo DialogmeldingKodeverk.HENVENDELSE_OM_PASIENT_HENVENDELSE_OM_SYKEFRAVARSOPPFOLGING.arenaNotatTittel
    }

    @Test
    internal fun `Tester mapping av arena tekstNotatInnhold`() {
        val felleformatDm = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
        )

        val msgHead: XMLMsgHead = felleformatDm.get()
        val emottakblokk = felleformatDm.get<XMLMottakenhetBlokk>()
        val personNumberPatient = extractInnbyggerident(felleformatDm) ?: ""
        val personNumberDoctor = emottakblokk.avsenderFnrFraDigSignatur
        val tssid = "1321415"

        val dialomeldingxml = extractDialogmelding(felleformatDm)
        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"

        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)

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
            emottakblokk,
            dialogmelding
        )

        arenaDialogNotat.notatTekst shouldBeEqualTo "Hei,Det gjelder pas. Sender sm2013 som vedlegg"
    }
}
