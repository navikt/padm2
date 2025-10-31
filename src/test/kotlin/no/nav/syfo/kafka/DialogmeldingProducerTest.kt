package no.nav.syfo.kafka

import io.mockk.*
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.model.*
import no.nav.syfo.util.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

internal class DialogmeldingProducerTest {

    lateinit var kafkaProducerMock: KafkaProducer<String, DialogmeldingForKafka>
    lateinit var dialogmeldingProducer: DialogmeldingProducer
    lateinit var fellesformat: XMLEIFellesformat
    lateinit var receivedDialogmelding: ReceivedDialogmelding
    lateinit var msgHead: XMLMsgHead
    lateinit var journalpostId: String

    @BeforeEach
    fun before() {
        kafkaProducerMock = mockk<KafkaProducer<String, DialogmeldingForKafka>>(relaxed = true)
        dialogmeldingProducer = DialogmeldingProducer(
            kafkaProducerDialogmelding = kafkaProducerMock,
        )
    }

    @Test
    internal fun `Tester mapping fra fellesformat til Kafka topic`() {
        setupTestData(getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat.xml"))

        dialogmeldingProducer.sendDialogmelding(
            receivedDialogmelding = receivedDialogmelding,
            msgHead = msgHead,
            journalpostId = journalpostId,
            antallVedlegg = fellesformat.calculateNumberOfVedlegg(),
        )
        val slot = slot<ProducerRecord<String, DialogmeldingForKafka>>()

        verify { kafkaProducerMock.send(capture(slot)) }

        val producerRecord = slot.captured
        assertEquals("teamsykefravr.dialogmelding", producerRecord.topic())
        assertEquals(msgHead.msgInfo.msgId, producerRecord.key() as String)
        val dialogmeldingForKafka = producerRecord.value()
        assertEquals(msgHead.msgInfo.msgId, dialogmeldingForKafka.msgId)
        assertEquals("DIALOG_NOTAT", dialogmeldingForKafka.msgType)
        assertEquals(0, dialogmeldingForKafka.antallVedlegg)
        assertEquals(msgHead.msgInfo.conversationRef.refToConversation, dialogmeldingForKafka.conversationRef)
        assertEquals(msgHead.msgInfo.conversationRef.refToParent, dialogmeldingForKafka.parentRef)
    }

    @Test
    internal fun `Tester mapping fra fellesformat til Kafka topic manglende conversationRef`() {
        setupTestData(getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat_dnr.xml"))

        dialogmeldingProducer.sendDialogmelding(
            receivedDialogmelding = receivedDialogmelding,
            msgHead = msgHead,
            journalpostId = journalpostId,
            antallVedlegg = fellesformat.calculateNumberOfVedlegg(),
        )
        val slot = slot<ProducerRecord<String, DialogmeldingForKafka>>()

        verify { kafkaProducerMock.send(capture(slot)) }

        val producerRecord = slot.captured
        assertEquals("teamsykefravr.dialogmelding", producerRecord.topic())
        assertEquals(msgHead.msgInfo.msgId, producerRecord.key() as String)
        val dialogmeldingForKafka = producerRecord.value()
        assertEquals(msgHead.msgInfo.msgId, dialogmeldingForKafka.msgId)
        assertEquals("DIALOG_NOTAT", dialogmeldingForKafka.msgType)
        assertEquals(0, dialogmeldingForKafka.antallVedlegg)
        assertNull(dialogmeldingForKafka.conversationRef)
        assertNull(dialogmeldingForKafka.parentRef)
    }

    @Test
    internal fun `Tester mapping fra fellesformat til Kafka topic med vedlegg`() {
        setupTestData(getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml"))

        dialogmeldingProducer.sendDialogmelding(
            receivedDialogmelding = receivedDialogmelding,
            msgHead = msgHead,
            journalpostId = journalpostId,
            antallVedlegg = fellesformat.calculateNumberOfVedlegg(),
        )
        val slot = slot<ProducerRecord<String, DialogmeldingForKafka>>()

        verify { kafkaProducerMock.send(capture(slot)) }

        val producerRecord = slot.captured
        assertEquals("teamsykefravr.dialogmelding", producerRecord.topic())
        assertEquals(msgHead.msgInfo.msgId, producerRecord.key() as String)
        val dialogmeldingForKafka = producerRecord.value()
        assertEquals(msgHead.msgInfo.msgId, dialogmeldingForKafka.msgId)
        assertEquals("DIALOG_NOTAT", dialogmeldingForKafka.msgType)
        assertEquals(2, dialogmeldingForKafka.antallVedlegg)

        val fellesformatFromKafkaMessage = safeUnmarshal(dialogmeldingForKafka.fellesformatXML)
        assertEquals(0, fellesformatFromKafkaMessage.calculateNumberOfVedlegg())
    }

    @Test
    internal fun `Tester mapping fra fellesformat til Kafka topic med vedlegg med mimetype jpg`() {
        setupTestData(
            getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml").replace("application/pdf", "image/jpg")
        )

        dialogmeldingProducer.sendDialogmelding(
            receivedDialogmelding = receivedDialogmelding,
            msgHead = msgHead,
            journalpostId = journalpostId,
            antallVedlegg = fellesformat.calculateNumberOfVedlegg(),
        )
        val slot = slot<ProducerRecord<String, DialogmeldingForKafka>>()

        verify { kafkaProducerMock.send(capture(slot)) }

        val producerRecord = slot.captured
        assertEquals("teamsykefravr.dialogmelding", producerRecord.topic())
        assertEquals(msgHead.msgInfo.msgId, producerRecord.key() as String)
        val dialogmeldingForKafka = producerRecord.value()
        assertEquals(msgHead.msgInfo.msgId, dialogmeldingForKafka.msgId)
        assertEquals("DIALOG_NOTAT", dialogmeldingForKafka.msgType)
        assertEquals(2, dialogmeldingForKafka.antallVedlegg)

        val fellesformatFromKafkaMessage = safeUnmarshal(dialogmeldingForKafka.fellesformatXML)
        assertEquals(0, fellesformatFromKafkaMessage.calculateNumberOfVedlegg())
    }

    @Test
    internal fun `Tester mapping fra fellesformat til Kafka topic for svar dialogmote`() {
        setupTestData(getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_svar_innkalling_dialogmote.xml"))

        dialogmeldingProducer.sendDialogmelding(
            receivedDialogmelding = receivedDialogmelding,
            msgHead = msgHead,
            journalpostId = journalpostId,
            antallVedlegg = fellesformat.calculateNumberOfVedlegg(),
        )
        val slot = slot<ProducerRecord<String, DialogmeldingForKafka>>()

        verify { kafkaProducerMock.send(capture(slot)) }

        val producerRecord = slot.captured
        assertEquals("teamsykefravr.dialogmelding", producerRecord.topic())
        assertEquals(msgHead.msgInfo.msgId, producerRecord.key() as String)
        val dialogmeldingForKafka = producerRecord.value()
        assertEquals(msgHead.msgInfo.msgId, dialogmeldingForKafka.msgId)
        assertEquals("DIALOG_SVAR", dialogmeldingForKafka.msgType)
        assertEquals(0, dialogmeldingForKafka.antallVedlegg)
        assertEquals("1", dialogmeldingForKafka.dialogmelding.innkallingMoterespons!!.temaKode.v)
    }

    fun setupTestData(inputMessageText: String) {
        fellesformat = safeUnmarshal(inputMessageText)

        val dialogmeldingId = UUID.randomUUID().toString()
        val emottakblokk = fellesformat.get<XMLMottakenhetBlokk>()
        val ediLoggId = emottakblokk.ediLoggId
        val msgId = fellesformat.get<XMLMsgHead>().msgInfo.msgId

        val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
        val innbyggerident = extractInnbyggerident(fellesformat)!!
        val personNumberDoctor = emottakblokk.avsenderFnrFraDigSignatur
        val legekontorOrgName = extractSenderOrganisationName(fellesformat)
        val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
        val dialogmeldingType = findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction)
        val legeHpr = extractLegeHpr(dialogmeldingId, fellesformat)

        val dialomeldingxml = extractDialogmelding(fellesformat)
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"

        val dialogmelding = dialomeldingxml.toDialogmelding(
            dialogmeldingId = dialogmeldingId,
            dialogmeldingType = dialogmeldingType,
            signaturDato = signaturDato,
            navnHelsePersonellNavn = navnHelsePersonellNavn
        )
        msgHead = fellesformat.get()
        receivedDialogmelding = ReceivedDialogmelding(
            dialogmelding = dialogmelding,
            personNrPasient = innbyggerident,
            personNrLege = personNumberDoctor,
            navLogId = ediLoggId,
            msgId = msgId,
            legekontorOrgNr = legekontorOrgNr,
            legekontorOrgName = legekontorOrgName,
            legekontorHerId = legekontorHerId,
            mottattDato = emottakblokk.mottattDatotid.toGregorianCalendar().toZonedDateTime()
                .withZoneSameInstant(
                    ZoneOffset.UTC
                ).toLocalDateTime(),
            legehpr = legeHpr,
            fellesformat = inputMessageText,
        )
        journalpostId = "jpid"
    }
}

fun XMLEIFellesformat.calculateNumberOfVedlegg(): Int {
    return this.get<XMLMsgHead>().document.count { it.isVedlegg() }
}
