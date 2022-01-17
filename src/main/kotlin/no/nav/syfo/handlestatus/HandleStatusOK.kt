package no.nav.syfo.handlestatus

import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
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
    session: Session,
    database: DatabaseInterface,
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
    pasientNavn: String,
    navnSignerendeLege: String,
    samhandlerPraksis: SamhandlerPraksis?,
    pasientAktoerId: String,
    legeAktoerId: String,
) {
    val journalpostId = journalService.onJournalRequest(
        receivedDialogmelding,
        validationResult,
        vedleggListe,
        loggingMeta,
        pasientNavn,
        navnSignerendeLege
    )

    if (!database.erDialogmeldingOpplysningerSendtArena(receivedDialogmelding.dialogmelding.id)) {
        sendArenaDialogNotat(
            arenaProducer, session,
            createArenaDialogNotat(
                fellesformat,
                samhandlerPraksis?.tss_ident,
                receivedDialogmelding.personNrLege,
                receivedDialogmelding.personNrPasient,
                msgHead,
                receiverBlock,
                receivedDialogmelding.dialogmelding,
            ),
            loggingMeta
        )
        database.lagreSendtArena(receivedDialogmelding.dialogmelding.id)
    }

    if (!database.erDialogmeldingOpplysningerSendtKafka(receivedDialogmelding.dialogmelding.id)) {
        dialogmeldingProducer.sendDialogmelding(
            receivedDialogmelding = receivedDialogmelding,
            msgHead = msgHead,
            journalpostId = journalpostId,
            antallVedlegg = vedleggListe?.size ?: 0,
            pasientAktoerId = pasientAktoerId,
            legeAktoerId = legeAktoerId,
        )
        database.lagreSendtKafka(receivedDialogmelding.dialogmelding.id)
    }

    if (!database.erDialogmeldingOpplysningerSendtApprec(receivedDialogmelding.dialogmelding.id)) {
        sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
        logger.info("Apprec Receipt with status OK sent to {}, {}", apprecQueueName, StructuredArguments.fields(loggingMeta))
        database.lagreSendtApprec(receivedDialogmelding.dialogmelding.id)
    }
}
