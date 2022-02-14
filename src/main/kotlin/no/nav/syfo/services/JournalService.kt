package no.nav.syfo.services

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.createJournalpostPayload
import no.nav.syfo.client.createPdfPayload
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.metrics.MELDING_LAGER_I_JOARK
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.persistering.db.hentDialogmeldingOpplysningerJournalpostId
import no.nav.syfo.persistering.db.lagreJournalforing
import no.nav.syfo.util.LoggingMeta

class JournalService(
    private val dokArkivClient: DokArkivClient,
    private val pdfgenClient: PdfgenClient,
    private val database: DatabaseInterface,
) {
    suspend fun onJournalRequest(
        receivedDialogmelding: ReceivedDialogmelding,
        validationResult: ValidationResult,
        vedleggListe: List<Vedlegg>?,
        loggingMeta: LoggingMeta,
        pasientNavn: String,
        navnSignerendeLege: String,
    ): String {
        val journalpostId = database.hentDialogmeldingOpplysningerJournalpostId(receivedDialogmelding.dialogmelding.id)
        return if (journalpostId == null) {

            logger.info("Prover aa lagre i Joark {}", StructuredArguments.fields(loggingMeta))

            val antallVedlegg = vedleggListe?.size ?: 0

            val pdfPayload = createPdfPayload(
                receivedDialogmelding.dialogmelding,
                validationResult,
                receivedDialogmelding.personNrPasient,
                pasientNavn,
                navnSignerendeLege,
                antallVedlegg
            )
            val pdf = pdfgenClient.createPdf(pdfPayload)
            logger.info("PDF generert {}", StructuredArguments.fields(loggingMeta))

            val journalpostPayload = createJournalpostPayload(
                receivedDialogmelding.dialogmelding,
                pdf,
                receivedDialogmelding.personNrLege,
                receivedDialogmelding.navLogId,
                receivedDialogmelding.dialogmelding.signaturDato,
                validationResult,
                receivedDialogmelding.personNrPasient,
                vedleggListe
            )
            val journalpost = dokArkivClient.createJournalpost(journalpostPayload, loggingMeta)

            MELDING_LAGER_I_JOARK.inc()
            logger.info(
                "Melding lagret i Joark med journalpostId {}, {}",
                journalpost.journalpostId,
                StructuredArguments.fields(loggingMeta)
            )
            database.lagreJournalforing(
                dialogmeldingid = receivedDialogmelding.dialogmelding.id,
                journalpostId = journalpost.journalpostId,
            )
            journalpost.journalpostId
        } else {
            journalpostId
        }
    }
}
