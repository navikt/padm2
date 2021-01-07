package no.nav.syfo.services

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.createJournalpostPayload
import no.nav.syfo.client.createPdfPayload
import no.nav.syfo.log
import no.nav.syfo.metrics.MELDING_LAGER_I_JOARK
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class JournalService(
    private val sakClient: SakClient,
    private val dokArkivClient: DokArkivClient,
    private val pdfgenClient: PdfgenClient
) {
    suspend fun onJournalRequest(
        receivedDialogmelding: ReceivedDialogmelding,
        validationResult: ValidationResult,
        vedleggListe: List<Vedlegg>?,
        loggingMeta: LoggingMeta,
        pasientNavn: String,
        navnSignerendeLege: String
    ) {
            log.info("Prover aa lagre i Joark {}", StructuredArguments.fields(loggingMeta))

            val sak = sakClient.findOrCreateSak(receivedDialogmelding.pasientAktoerId, receivedDialogmelding.msgId,
                    loggingMeta)

            val pdfPayload = createPdfPayload(
                receivedDialogmelding.dialogmelding,
                validationResult,
                receivedDialogmelding.personNrPasient,
                pasientNavn,
                navnSignerendeLege)
            val pdf = pdfgenClient.createPdf(pdfPayload)
            log.info("PDF generert {}", StructuredArguments.fields(loggingMeta))

            val journalpostPayload = createJournalpostPayload(
                receivedDialogmelding.dialogmelding,
                sak.id.toString(),
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
            log.info("Melding lagret i Joark med journalpostId {}, {}",
                    journalpost.journalpostId,
                    StructuredArguments.fields(loggingMeta))
        }
}
