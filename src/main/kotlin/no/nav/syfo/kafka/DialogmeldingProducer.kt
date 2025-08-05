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
) {
    fun sendDialogmelding(
        receivedDialogmelding: ReceivedDialogmelding,
        msgHead: XMLMsgHead,
        journalpostId: String,
        antallVedlegg: Int,
    ) {
        try {
            val dialogmeldingForKafka = createDialogmeldingForKafka(
                receivedDialogmelding = receivedDialogmelding,
                msgHead = msgHead,
                journalpostId = journalpostId,
                antallVedlegg = antallVedlegg,
            )
            kafkaProducerDialogmelding.send(
                ProducerRecord(
                    DIALOGMELDING_TOPIC,
                    dialogmeldingForKafka.msgId,
                    dialogmeldingForKafka,
                )
            ).also { it.get() }
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send dialogmelding with id {}: ${e.message}",
                receivedDialogmelding.msgId,
                e
            )
            throw e
        }
    }

    fun createDialogmeldingForKafka(
        receivedDialogmelding: ReceivedDialogmelding,
        msgHead: XMLMsgHead,
        journalpostId: String,
        antallVedlegg: Int,
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
            personIdentBehandler = receivedDialogmelding.personNrLege,
            legekontorOrgNr = receivedDialogmelding.legekontorOrgNr?.value,
            legekontorHerId = receivedDialogmelding.legekontorHerId,
            legekontorOrgName = receivedDialogmelding.legekontorOrgName,
            legehpr = receivedDialogmelding.legehpr,
            dialogmelding = receivedDialogmelding.dialogmelding,
            antallVedlegg = antallVedlegg,
            journalpostId = journalpostId,
            fellesformatXML = cloneAndPrune(receivedDialogmelding.fellesformat),
        )
    }

    fun cloneAndPrune(fellesformatString: String): String {
        val fellesformat = safeUnmarshal(fellesformatString).also {
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
