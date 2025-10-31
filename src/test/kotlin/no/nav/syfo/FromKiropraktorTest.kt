package no.nav.syfo

import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.createArenaDialogNotat
import no.nav.syfo.client.createAvsender
import no.nav.syfo.model.DialogmeldingKodeverk
import no.nav.syfo.model.findDialogmeldingType
import no.nav.syfo.model.toDialogmelding
import no.nav.syfo.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.LocalDateTime
import java.util.*

class FromKiropraktorTest {

    private val BEHANDLER_FNR = "21312341414"
    private val BEHANDLER_ETTERNAVN = "Kiropraktorsen"
    private val BEHANDLER_FORNAVN = "Lisa"
    private val TSS_ID = "1321415"

    @Test
    internal fun `Find behandler in Notat instead of HeadMsg`() {
        // We need to support behandler details in Notat instead of msgHead, because it was allowed in Eia
        val fellesformat = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_kiropraktor.xml")
        )
        val behandler = extractBehandler(fellesformat)

        val avsender = createAvsender(BEHANDLER_FNR, null, behandler)

        assertEquals(BEHANDLER_FNR, avsender.lege.legeFnr)
        assertEquals(BigInteger("0"), avsender.lege.tssId)
        assertEquals(BEHANDLER_ETTERNAVN, avsender.lege.legeNavn.etternavn)
        assertEquals(BEHANDLER_FORNAVN, avsender.lege.legeNavn.fornavn)
        assertEquals("", avsender.lege.legeNavn.mellomnavn)
    }

    @Test
    internal fun `Create ArenaDialogNotat from Kiropraktor message`() {
        val fellesformat = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_kiropraktor.xml")
        )
        val msgHead: XMLMsgHead = fellesformat.get()
        val emottakblokk = fellesformat.get<XMLMottakenhetBlokk>()

        val dialogmelding = extractDialogmelding(fellesformat).toDialogmelding(
            dialogmeldingId = UUID.randomUUID().toString(),
            dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction),
            signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0),
            navnHelsePersonellNavn = extractBehandlerNavn(fellesformat)
        )

        val arenaDialogNotat = createArenaDialogNotat(
            fellesformat = fellesformat,
            tssid = TSS_ID,
            legefnr = emottakblokk.avsenderFnrFraDigSignatur,
            innbyggerident = extractInnbyggerident(fellesformat) ?: "",
            msgHead = msgHead,
            emottakblokk = emottakblokk,
            dialogmelding = dialogmelding
        )

        assertEquals("Henvendelse til NAV", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentNavn)
        assertEquals("0f1daa95-45d0-4a18-8d5d-b79bd069d3b7", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentreferanse)
        assertEquals("2102090840kiro66788.1", arenaDialogNotat.eiaDokumentInfo.dokumentInfo.ediLoggId)
        assertEquals("10108000398", arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeFnr)
        assertEquals(TSS_ID.toBigInteger(), arenaDialogNotat.eiaDokumentInfo.avsender.lege.tssId)
        assertEquals("Lisa", arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.fornavn)
        assertEquals("Kiropraktorsen", arenaDialogNotat.eiaDokumentInfo.avsender.lege.legeNavn.etternavn)
        assertEquals("01234567890", arenaDialogNotat.pasientData.person.personFnr)
        assertEquals("Nakke", arenaDialogNotat.pasientData.person.personNavn.fornavn)
        assertEquals("", arenaDialogNotat.pasientData.person.personNavn.mellomnavn)
        assertEquals("Kinken", arenaDialogNotat.pasientData.person.personNavn.etternavn)
        assertEquals("3", arenaDialogNotat.notatKategori)
        assertEquals("31", arenaDialogNotat.notatKode)
        assertEquals(DialogmeldingKodeverk.HENVENDELSE_OM_PASIENT_HENVENDELSE_OM_SYKEFRAVARSOPPFOLGING.arenaNotatTittel, arenaDialogNotat.notatTittel)
        assertEquals("Kink i nakken.\n                                Mvh Lisa Kiropraktorsen", arenaDialogNotat.notatTekst)
        assertEquals(LocalDateTime.of(2021, 2, 9, 8, 32, 3), arenaDialogNotat.notatDato)
    }
}
