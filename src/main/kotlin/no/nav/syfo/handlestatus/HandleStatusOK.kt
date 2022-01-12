package no.nav.syfo.handlestatus

import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.client.createArenaDialogNotat
import no.nav.syfo.client.sendArenaDialogNotat
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.persistering.handleRecivedMessage
import no.nav.syfo.services.JournalService
import no.nav.syfo.services.sendReceipt
import no.nav.syfo.util.LoggingMeta

suspend fun handleStatusOK(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta,
    apprecQueueName: String,
    journalService: JournalService,
    dialogmeldingProducer: DialogmeldingProducer,
    receivedDialogmelding: ReceivedDialogmelding,
    validationResult: ValidationResult,
    vedleggListe: List<Vedlegg>?,
    arenaProducer: MessageProducer,
    msgHead: XMLMsgHead,
    receiverBlock: XMLMottakenhetBlokk,
    dialogmelding: Dialogmelding,
    database: DatabaseInterface,
    pasientNavn: String,
    navnSignerendeLege: String,
    sha256String: String,
) {

    val journalpostResponse = journalService.onJournalRequest(
        receivedDialogmelding,
        validationResult,
        vedleggListe,
        loggingMeta,
        pasientNavn,
        navnSignerendeLege
    )

    sendArenaDialogNotat(
        arenaProducer, session,
        createArenaDialogNotat(
            fellesformat,
            receivedDialogmelding.tssid,
            receivedDialogmelding.personNrLege,
            receivedDialogmelding.personNrPasient,
            msgHead,
            receiverBlock,
            dialogmelding
        ),
        loggingMeta
    )

    handleRecivedMessage(receivedDialogmelding, validationResult, sha256String, loggingMeta, database)

    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
    logger.info("Apprec Receipt with status OK sent to {}, {}", apprecQueueName, StructuredArguments.fields(loggingMeta))

    dialogmeldingProducer.sendDialogmelding(
        receivedDialogmelding = receivedDialogmelding,
        msgHead = msgHead,
        journalpostResponse = journalpostResponse,
        antallVedlegg = vedleggListe?.size ?: 0,
    )
}
