package no.nav.syfo.kafka

import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.model.*
import no.nav.syfo.util.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.io.*
import javax.xml.bind.Marshaller

class DialogmeldingProducer(
    private val kafkaProducerDialogmelding: KafkaProducer<String, DialogmeldingForKafka>,
    private val enabled: Boolean,
) {
    fun sendDialogmelding(
        receivedDialogmelding: ReceivedDialogmelding,
        msgHead: XMLMsgHead,
        journalpostId: String,
        antallVedlegg: Int,
        pasientAktoerId: String,
        legeAktoerId: String,
    ) {
        if (enabled) {
            try {
                val dialogmeldingForKafka = createDialogmeldingForKafka(
                    receivedDialogmelding = receivedDialogmelding,
                    msgHead = msgHead,
                    journalpostId = journalpostId,
                    antallVedlegg = antallVedlegg,
                    pasientAktoerId = pasientAktoerId,
                    legeAktoerId = legeAktoerId,
                )
                kafkaProducerDialogmelding.send(
                    ProducerRecord(
                        DIALOGMELDING_TOPIC,
                        dialogmeldingForKafka.msgId,
                        dialogmeldingForKafka,
                    )
                ).get()
            } catch (e: Exception) {
                log.error(
                    "Exception was thrown when attempting to send dialogmelding with id {}: ${e.message}",
                    receivedDialogmelding.msgId,
                    e
                )
                throw e
            }
        } else {
            log.info("Send to Kafka-topic disabled. Would have sent record with msgId ${msgHead.msgInfo.msgId} with conversationRef ${msgHead.msgInfo.conversationRef?.refToConversation}")
        }
    }

    fun createDialogmeldingForKafka(
        receivedDialogmelding: ReceivedDialogmelding,
        msgHead: XMLMsgHead,
        journalpostId: String,
        antallVedlegg: Int,
        pasientAktoerId: String,
        legeAktoerId: String,
    ): DialogmeldingForKafka {
        val xmlMsgInfo = msgHead.msgInfo
        return DialogmeldingForKafka(
            msgId = xmlMsgInfo.msgId,
            msgType = xmlMsgInfo.type.v,
            navLogId = receivedDialogmelding.navLogId,
            mottattTidspunkt = receivedDialogmelding.mottattDato,
            conversationRef = xmlMsgInfo.conversationRef?.refToConversation,
            parentRef = xmlMsgInfo.conversationRef?.refToParent,
            personIdentPasient = receivedDialogmelding.personNrPasient,
            pasientAktoerId = pasientAktoerId,
            personIdentBehandler = receivedDialogmelding.personNrLege,
            behandlerAktoerId = legeAktoerId,
            legekontorOrgNr = receivedDialogmelding.legekontorOrgNr,
            legekontorHerId = receivedDialogmelding.legekontorHerId,
            legekontorReshId = receivedDialogmelding.legekontorReshId,
            legekontorOrgName = receivedDialogmelding.legekontorOrgName,
            legehpr = receivedDialogmelding.legehpr,
            dialogmelding = receivedDialogmelding.dialogmelding,
            antallVedlegg = antallVedlegg,
            journalpostId = journalpostId,
            fellesformatXML = cloneAndPrune(receivedDialogmelding.fellesformat),
        )
    }

    fun cloneAndPrune(fellesformatString: String): String {
        val fellesformat = (fellesformatUnmarshaller.unmarshal(StringReader(fellesformatString)) as XMLEIFellesformat).also {
            it.removeVedlegg()
        }
        return StringWriter().use {
            fellesformatJaxBContext.createMarshaller().apply {
                setProperty(Marshaller.JAXB_ENCODING, "UTF-8")
            }.marshal(fellesformat, it)
            it.toString()
        }
    }

    companion object {
        const val DIALOGMELDING_TOPIC = "teamsykefravr.dialogmelding"
        private val log = LoggerFactory.getLogger(DialogmeldingProducer::class.java)
    }
}

fun XMLEIFellesformat.removeVedlegg() {
    this.get<XMLMsgHead>().document.removeAll { it.isVedlegg() }
}
