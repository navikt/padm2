package no.nav.syfo.services

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.*
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.metrics.MELDING_LAGER_I_JOARK
import no.nav.syfo.model.*
import no.nav.syfo.persistering.db.*
import no.nav.syfo.util.LoggingMeta

class JournalService(
    private val dokArkivClient: DokArkivClient,
    private val pdfgenClient: PdfgenClient,
    private val database: DatabaseInterface,
    private val jpRetryEnabled: Boolean = true,
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
                receivedDialogmelding.legehpr,
                receivedDialogmelding.navLogId,
                receivedDialogmelding.dialogmelding.signaturDato,
                validationResult,
                receivedDialogmelding.personNrPasient,
                vedleggListe
            )
            val journalpost = try {
                dokArkivClient.createJournalpost(journalpostPayload, loggingMeta)
            } catch (exc: Exception) {
                if (jpRetryEnabled) {
                    throw exc
                } else {
                    logger.warn("Journalføring failed, skipping retry: ", exc)
                }
                null
            }
            val journalpostId = journalpost?.journalpostId ?: "0"
            MELDING_LAGER_I_JOARK.increment()
            logger.info(
                "Melding lagret i Joark med journalpostId {}, {}",
                journalpostId,
                StructuredArguments.fields(loggingMeta)
            )
            database.lagreJournalforing(
                dialogmeldingid = receivedDialogmelding.dialogmelding.id,
                journalpostId = journalpostId,
            )
            journalpostId
        } else {
            journalpostId
        }
    }
}
