package no.nav.syfo

import java.io.StringReader
import java.time.LocalDateTime
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.createArenaDialogNotat
import no.nav.syfo.model.DialogmeldingKodeverk
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.getFileAsStringISO88591
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldEqual
import org.junit.Test

internal class CreateArenaDialogNotatTest {

    @Test
    internal fun `Tester mapping fra fellesformat til ArenaDialogNotat`() {
        val felleformatDm = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_svar_foresporsel_om_pasient.xml"))
        ) as XMLEIFellesformat

        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val msgHead: XMLMsgHead = felleformatDm.get()
        val receiverBlock = felleformatDm.get<XMLMottakenhetBlokk>()
        val personNumberPatient = msgHead.msgInfo.patient.ident.find { it.typeId.v == "FNR" }?.id ?: ""
        val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur
        val tssid = "1321415"

        val arenaDialogNotat = createArenaDialogNotat(
            felleformatDm,
            tssid,
            personNumberDoctor,
            personNumberPatient,
            msgHead,
            receiverBlock
        )

        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentType shouldBeEqualTo "DM"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentTypeVersjon shouldBeEqualTo "1.0"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentNavn shouldBeEqualTo "Svar på forespørsel"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentreferanse shouldBeEqualTo "b62016eb-6c2d-417a-8ecc-157b3c5ee2ca"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.ediLoggId shouldBeEqualTo "FiktivTestdata0001"
        arenaDialogNotat.eiaDokumentInfo.dokumentInfo.dokumentDato.shouldEqual(LocalDateTime.of(2019, 1, 16, 21, 51, 35, 531000000))
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
        arenaDialogNotat.notatDato.shouldEqual(LocalDateTime.of(2019, 1, 16, 21, 51, 35, 531000000))
    }
}
