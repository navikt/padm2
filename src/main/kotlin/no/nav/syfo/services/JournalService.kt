package no.nav.syfo.services

import io.ktor.util.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.*
import no.nav.syfo.log
import no.nav.syfo.metrics.MELDING_LAGER_I_JOARK
import no.nav.syfo.model.*
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class JournalService(
    private val dokArkivClient: DokArkivClient,
    private val pdfgenClient: PdfgenClient
) {
    suspend fun onJournalRequest(
        receivedDialogmelding: ReceivedDialogmelding,
        validationResult: ValidationResult,
        vedleggListe: List<Vedlegg>?,
        loggingMeta: LoggingMeta,
        pasientNavn: String,
        navnSignerendeLege: String,
    ): JournalpostResponse {
        log.info("Prover aa lagre i Joark {}", StructuredArguments.fields(loggingMeta))

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
        log.info("PDF generert {}", StructuredArguments.fields(loggingMeta))

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
        log.info(
            "Melding lagret i Joark med journalpostId {}, {}",
            journalpost.journalpostId,
            StructuredArguments.fields(loggingMeta)
        )
        return journalpost
    }
}
