package no.nav.syfo.kafka

import io.mockk.*
import java.io.StringReader
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.model.*
import no.nav.syfo.util.*
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.*
import java.time.ZoneOffset

internal class DialogmeldingProducerTest {

    lateinit var kafkaProducerMock: KafkaProducer<String, DialogmeldingForKafka>
    lateinit var dialogmeldingProducer: DialogmeldingProducer
    lateinit var fellesformat: XMLEIFellesformat
    lateinit var receivedDialogmelding: ReceivedDialogmelding
    lateinit var msgHead: XMLMsgHead
    lateinit var journalpostResponse: JournalpostResponse

    @Before
    fun before() {
        kafkaProducerMock = mockk<KafkaProducer<String, DialogmeldingForKafka>>(relaxed = true)
        dialogmeldingProducer = DialogmeldingProducer(
            kafkaProducerDialogmelding = kafkaProducerMock,
            enabled = true,
        )
    }

    @Test
    internal fun `Tester mapping fra fellesformat til Kafka topic`() {
        setupTestData(getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat.xml"))

        dialogmeldingProducer.sendDialogmelding(
            receivedDialogmelding = receivedDialogmelding,
            msgHead = msgHead,
            journalpostResponse = journalpostResponse,
            antallVedlegg = fellesformat.calculateNumberOfVedlegg(),
        )
        val slot = slot<ProducerRecord<String, DialogmeldingForKafka>>()

        verify { kafkaProducerMock.send(capture(slot)) }

        val producerRecord = slot.captured
        producerRecord.topic() shouldBeEqualTo "teamsykefravr.dialogmelding"
        producerRecord.key() as String shouldBeEqualTo msgHead.msgInfo.msgId
        val dialogmeldingForKafka = producerRecord.value()
        dialogmeldingForKafka.antallVedlegg shouldBe 0
        dialogmeldingForKafka.conversationRef shouldBe msgHead.msgInfo.conversationRef.refToConversation
        dialogmeldingForKafka.parentRef shouldBe msgHead.msgInfo.conversationRef.refToParent
    }

    @Test
    internal fun `Tester mapping fra fellesformat til Kafka topic manglende conversationRef`() {
        setupTestData(getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat_dnr.xml"))

        dialogmeldingProducer.sendDialogmelding(
            receivedDialogmelding = receivedDialogmelding,
            msgHead = msgHead,
            journalpostResponse = journalpostResponse,
            antallVedlegg = fellesformat.calculateNumberOfVedlegg(),
        )
        val slot = slot<ProducerRecord<String, DialogmeldingForKafka>>()

        verify { kafkaProducerMock.send(capture(slot)) }

        val producerRecord = slot.captured
        producerRecord.topic() shouldBeEqualTo "teamsykefravr.dialogmelding"
        producerRecord.key() as String shouldBeEqualTo msgHead.msgInfo.msgId
        val dialogmeldingForKafka = producerRecord.value()
        dialogmeldingForKafka.antallVedlegg shouldBe 0
        dialogmeldingForKafka.conversationRef shouldBe null
        dialogmeldingForKafka.parentRef shouldBe null
    }

    @Test
    internal fun `Tester mapping fra fellesformat til Kafka topic med vedlegg`() {
        setupTestData(getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml"))

        dialogmeldingProducer.sendDialogmelding(
            receivedDialogmelding = receivedDialogmelding,
            msgHead = msgHead,
            journalpostResponse = journalpostResponse,
            antallVedlegg = fellesformat.calculateNumberOfVedlegg(),
        )
        val slot = slot<ProducerRecord<String, DialogmeldingForKafka>>()

        verify { kafkaProducerMock.send(capture(slot)) }

        val producerRecord = slot.captured
        producerRecord.topic() shouldBeEqualTo "teamsykefravr.dialogmelding"
        producerRecord.key() as String shouldBeEqualTo msgHead.msgInfo.msgId
        val dialogmeldingForKafka = producerRecord.value()
        dialogmeldingForKafka.antallVedlegg shouldBe 2

        val fellesformatFromKafkaMessage = fellesformatUnmarshaller.unmarshal(StringReader(dialogmeldingForKafka.fellesformatXML)) as XMLEIFellesformat
        fellesformatFromKafkaMessage.calculateNumberOfVedlegg() shouldBe 0
    }

    fun setupTestData(inputMessageText: String) {
        fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat

        val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
        val ediLoggId = receiverBlock.ediLoggId
        val msgId = fellesformat.get<XMLMsgHead>().msgInfo.msgId

        val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
        val innbyggerident = extractInnbyggerident(fellesformat)!!
        val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur
        val legekontorOrgName = extractSenderOrganisationName(fellesformat)
        val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
        val legekontorReshId = extractOrganisationReshNumberFromSender(fellesformat)?.id
        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)
        val legeHpr = extractLegeHpr(fellesformat)

        val dialomeldingxml = extractDialogmelding(fellesformat)
        val dialogmeldingId = UUID.randomUUID().toString()
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
            pasientAktoerId = "",
            personNrLege = personNumberDoctor,
            legeAktoerId = "",
            navLogId = ediLoggId,
            msgId = msgId,
            legekontorOrgNr = legekontorOrgNr,
            legekontorOrgName = legekontorOrgName,
            legekontorHerId = legekontorHerId,
            legekontorReshId = legekontorReshId,
            mottattDato = receiverBlock.mottattDatotid.toGregorianCalendar().toZonedDateTime()
                .withZoneSameInstant(
                    ZoneOffset.UTC
                ).toLocalDateTime(),
            legehpr = legeHpr,
            fellesformat = inputMessageText,
            tssid = ""
        )
        journalpostResponse = JournalpostResponse(
            dokumenter = emptyList(),
            journalpostId = "jpid",
            journalpostferdigstilt = true,
        )
    }
}

fun XMLEIFellesformat.calculateNumberOfVedlegg(): Int {
    return this.get<XMLMsgHead>().document.count { it.isVedlegg() }
}
