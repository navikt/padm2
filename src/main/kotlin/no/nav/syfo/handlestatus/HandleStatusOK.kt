package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.client.*
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.logger
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.persistering.db.*
import no.nav.syfo.services.JournalService
import no.nav.syfo.services.sendReceipt
import no.nav.syfo.util.LoggingMeta

suspend fun handleStatusOK(
    database: DatabaseInterface,
    mqSender: MQSenderInterface,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta,
    journalService: JournalService,
    dialogmeldingProducer: DialogmeldingProducer,
    receivedDialogmelding: ReceivedDialogmelding,
    validationResult: ValidationResult,
    vedleggListe: List<Vedlegg>?,
    msgHead: XMLMsgHead,
    pasientNavn: String,
    navnSignerendeLege: String,
) {
    val journalpostId = journalService.onJournalRequest(
        receivedDialogmelding,
        validationResult,
        vedleggListe,
        loggingMeta,
        pasientNavn,
        navnSignerendeLege
    )

    if (!database.erDialogmeldingOpplysningerSendtKafka(receivedDialogmelding.dialogmelding.id)) {
        dialogmeldingProducer.sendDialogmelding(
            receivedDialogmelding = receivedDialogmelding,
            msgHead = msgHead,
            journalpostId = journalpostId,
            antallVedlegg = vedleggListe?.size ?: 0,
        )
        database.lagreSendtKafka(receivedDialogmelding.dialogmelding.id)
    }

    if (!database.erDialogmeldingOpplysningerSendtApprec(receivedDialogmelding.dialogmelding.id)) {
        sendReceipt(mqSender, fellesformat, ApprecStatus.OK)
        logger.info("Apprec Receipt with status OK sent, {}", StructuredArguments.fields(loggingMeta))
        database.lagreSendtApprec(receivedDialogmelding.dialogmelding.id)
    }
}
