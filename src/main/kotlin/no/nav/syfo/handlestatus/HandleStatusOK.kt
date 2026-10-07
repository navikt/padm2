package no.nav.syfo.handlestatus

import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.*
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.persistering.db.*
import no.nav.syfo.services.ApprecService
import no.nav.syfo.services.JournalService
import no.nav.syfo.util.LoggingMeta

suspend fun handleStatusOK(
    database: DatabaseInterface,
    apprecService: ApprecService,
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

    apprecService.ferdigstillOk(receivedDialogmelding, loggingMeta)
}
