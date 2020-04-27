package no.nav.syfo.handlestatus

import io.ktor.util.KtorExperimentalAPI
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.log
import no.nav.syfo.model.DialogmeldingSak
import no.nav.syfo.services.sendReceipt
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
fun handleStatusOK(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta,
    apprecQueueName: String,
    kafkaProducerDialogmeldingSak: KafkaProducer<String, DialogmeldingSak>,
    padm2SakTopic: String,
    dialogmeldingSak: DialogmeldingSak
) {

    kafkaProducerDialogmeldingSak.send(
        ProducerRecord(padm2SakTopic, dialogmeldingSak)
    )

    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
    log.info("Apprec Receipt sent to {}, {}", apprecQueueName, StructuredArguments.fields(loggingMeta))
}
