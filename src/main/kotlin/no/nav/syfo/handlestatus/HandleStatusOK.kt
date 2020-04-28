package no.nav.syfo.handlestatus

import io.ktor.util.KtorExperimentalAPI
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.services.JournalService
import no.nav.syfo.services.sendReceipt
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
suspend fun handleStatusOK(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta,
    apprecQueueName: String,
    journalService: JournalService,
    receivedDialogmelding: ReceivedDialogmelding,
    validationResult: ValidationResult,
    vedleggListe: List<Vedlegg>?
) {

    journalService.onJournalRequest(
        receivedDialogmelding,
        validationResult,
        vedleggListe,
        loggingMeta
    )

    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
    log.info("Apprec Receipt sent to {}, {}", apprecQueueName, StructuredArguments.fields(loggingMeta))
}
