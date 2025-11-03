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
import org.junit.jupiter.api.Assertions.assertEquals
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

        assertEquals("DM", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentType)
        assertEquals("1.0", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentTypeVersjon)
        assertEquals("Svar på forespørsel", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentNavn)
        assertEquals("b62016eb-6c2d-417a-8ecc-157b3c5ee2ca", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentreferanse)
        assertEquals("FiktivTestdata0001", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.ediLoggId)
        assertEquals(FASTLEGE_FNR, arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeFnr)
        assertEquals(tssid.toBigInteger(), arenaDialogNotat.eiaDokumentInfo.avsender.lege.tssId)
        assertEquals("Inga", arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.fornavn)
        assertEquals("Valda", arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.etternavn)
        assertEquals("3143242342", arenaDialogNotat.pasientData.person.personFnr)
        assertEquals("Etternavn", arenaDialogNotat.pasientData.person.personNavn.fornavn)
        assertEquals("", arenaDialogNotat.pasientData.person.personNavn.mellomnavn)
        assertEquals("Test", arenaDialogNotat.pasientData.person.personNavn.etternavn)
        assertEquals("2", arenaDialogNotat.notatKategori)
        assertEquals("25", arenaDialogNotat.notatKode)
        assertEquals(DialogmeldingKodeverk.OVERFORING_EPJ_INFORMASJON_SVAR_PAA_FORESPORSEL_OM_PASIENT.arenaNotatTittel, arenaDialogNotat.notatTittel)
        assertEquals("Pasieten har masse info her", arenaDialogNotat.notatTekst)
        assertEquals("OD1812186729156", arenaDialogNotat.svarReferanse)
    }

    @Test
    internal fun `Default to 0 when tss id is empty string`() {
        val behandler = Behandler(etternavn = "Etternavn", fornavn = "Fornavn", mellomnavn = "Mellomnavn")
        val avsender = createAvsender(FASTLEGE_FNR, "", behandler)
        assertEquals(FASTLEGE_FNR, avsender.lege.legeFnr)
        assertEquals(BigInteger("0"), avsender.lege.tssId)
    }

    @Test
    internal fun `Default to 0 when tss id is null`() {
        val behandler = Behandler("Etternavn", "Fornavn", "Mellomnavn")
        val avsender = createAvsender(FASTLEGE_FNR, null, behandler)
        assertEquals(FASTLEGE_FNR, avsender.lege.legeFnr)
        assertEquals(BigInteger("0"), avsender.lege.tssId)
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

        assertEquals("DM", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentType)
        assertEquals("1.0", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentTypeVersjon)
        assertEquals("Svar p.. foresp..rsel", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentNavn)
        assertEquals("9be88bc5-4219-473e-954b-c0dd115ff4e0", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentreferanse)
        assertEquals("1901162204amsa22108.1", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.ediLoggId)
        assertEquals(2020, arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentDato.year)
        assertEquals(9, arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentDato.monthValue)
        assertEquals(21, arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentDato.dayOfMonth)
        assertEquals("01010112377", arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeFnr)
        assertEquals(tssid.toBigInteger(), arenaDialogNotat.eiaDokumentInfo.avsender.lege.tssId)
        assertEquals("Inga", arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.fornavn)
        assertEquals("Valda", arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.etternavn)
        assertEquals("01010142365", arenaDialogNotat.pasientData.person.personFnr)
        assertEquals("Etternavn", arenaDialogNotat.pasientData.person.personNavn.fornavn)
        assertEquals("", arenaDialogNotat.pasientData.person.personNavn.mellomnavn)
        assertEquals("Test", arenaDialogNotat.pasientData.person.personNavn.etternavn)
        assertEquals("1", arenaDialogNotat.notatKategori)
        assertEquals("11", arenaDialogNotat.notatKode)
        assertEquals(DialogmeldingKodeverk.SVAR_PAA_INNKALLING_DIALOGMOTE_JA_JEG_KOMMER.arenaNotatTittel, arenaDialogNotat.notatTittel)
        assertEquals("Ta gjerne kontakt ang hvilket telefonnummer jeg skal ringe. Mvh Inga F. Valda.", arenaDialogNotat.notatTekst)
        assertEquals("OD2009169905747", arenaDialogNotat.svarReferanse)
        assertEquals(2020, arenaDialogNotat.notatDato.year)
        assertEquals(9, arenaDialogNotat.notatDato.monthValue)
        assertEquals(21, arenaDialogNotat.notatDato.dayOfMonth)
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

        assertEquals("DM", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentType)
        assertEquals("1.0", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentTypeVersjon)
        assertEquals("Notat", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentNavn)
        assertEquals("37340D30-FE14-42B5-985F-A8FF8FFA0CB5", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentreferanse)
        assertEquals("1901162157lege21826.1", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.ediLoggId)
        assertEquals(localDateTime, arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentDato)
        assertEquals("01010112377", arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeFnr)
        assertEquals(tssid.toBigInteger(), arenaDialogNotat.eiaDokumentInfo.avsender.lege.tssId)
        assertEquals("Inga", arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.fornavn)
        assertEquals("Valda", arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.etternavn)
        assertEquals("01010142365", arenaDialogNotat.pasientData.person.personFnr)
        assertEquals("Etternavn", arenaDialogNotat.pasientData.person.personNavn.fornavn)
        assertEquals("", arenaDialogNotat.pasientData.person.personNavn.mellomnavn)
        assertEquals("Test", arenaDialogNotat.pasientData.person.personNavn.etternavn)
        assertEquals("3", arenaDialogNotat.notatKategori)
        assertEquals("31", arenaDialogNotat.notatKode)
        assertEquals(DialogmeldingKodeverk.HENVENDELSE_OM_PASIENT_HENVENDELSE_OM_SYKEFRAVARSOPPFOLGING.arenaNotatTittel, arenaDialogNotat.notatTittel)
        assertEquals("Hei,Det gjelder pas. Sender som vedlegg epikrisen", arenaDialogNotat.notatTekst)
        assertEquals("A1578B81-0042-453B-8527-6CF182BDA6C7", arenaDialogNotat.svarReferanse)
        assertEquals(localDateTime, arenaDialogNotat.notatDato)
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

        assertEquals("45088649080", arenaDialogNotat.pasientData.person.personFnr)
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

        assertEquals("3", arenaDialogNotat.notatKategori)
        assertEquals("31", arenaDialogNotat.notatKode)
        assertEquals(DialogmeldingKodeverk.HENVENDELSE_OM_PASIENT_HENVENDELSE_OM_SYKEFRAVARSOPPFOLGING.arenaNotatTittel, arenaDialogNotat.notatTittel)
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

        assertEquals("Hei,Det gjelder pas. Sender sm2013 som vedlegg", arenaDialogNotat.notatTekst)
    }
}
