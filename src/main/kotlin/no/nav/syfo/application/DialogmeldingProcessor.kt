package no.nav.syfo.application

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.Environment
import no.nav.syfo.client.*
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.handlestatus.*
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.logger
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.*
import no.nav.syfo.persistering.persistRecivedMessageValidation
import no.nav.syfo.services.*
import no.nav.syfo.util.*
import io.ktor.client.HttpClient
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class DialogmeldingProcessor(
    val database: DatabaseInterface,
    val env: Environment,
    val apprecService: ApprecService,
    val validationService: ValidationService,
    val vedleggService: VedleggService,
    val dialogmeldingProducer: DialogmeldingProducer,
    val azureAdV2Client: AzureAdV2Client,
    val httpClient: HttpClient,
    val httpClientPdfgen: HttpClient,
) {
    val pdfgenClient = PdfgenClient(
        url = env.syfopdfgen,
        httpClient = httpClientPdfgen,
    )
    val dokArkivClient = DokArkivClient(
        azureAdV2Client = azureAdV2Client,
        dokArkivClientId = env.dokArkivClientId,
        url = env.dokArkivUrl,
        httpClient = httpClient,
    )
    val syfohelsenettproxyClient = SyfohelsenettproxyClient(
        azureAdV2Client = azureAdV2Client,
        endpointUrl = env.syfohelsenettproxyEndpointURL,
        helsenettClientId = env.syfohelsenettproxyClientId,
        httpClient = httpClient,
    )
    val journalService = JournalService(
        dokArkivClient = dokArkivClient,
        pdfgenClient = pdfgenClient,
        database = database,
        jpRetryEnabled = env.jpRetryEnabled,
    )
    val signerendeLegeService = SignerendeLegeService(
        syfohelsenettproxyClient = syfohelsenettproxyClient,
    )

    suspend fun process(
        receivedDialogmelding: ReceivedDialogmelding,
    ) {
        val loggingMeta = receivedDialogmelding.loggingMeta()
        val starttime = System.currentTimeMillis()

        val processingDelay = processingDelay(receivedDialogmelding.dialogmeldingType)
        if (
            !processingDelay.isZero &&
            receivedDialogmelding.mottattDato.isAfter(LocalDateTime.now(ZoneId.of("Europe/Oslo")).minus(processingDelay))
        ) {
            // Delay henvendelser to allow time for sykmelding and oppfolgingstilfelle to be updated.
            // RerunCronJob will process the henvendelse when the delay has passed.
            logger.info("Delaying processing of henvendelse, {}", StructuredArguments.fields(loggingMeta))
            return
        }

        val navnSignerendeLege = signerendeLegeService.signerendeLegeNavn(
            signerendeLegeFnr = receivedDialogmelding.personNrLege,
            msgId = receivedDialogmelding.msgId,
            loggingMeta = loggingMeta,
        )

        val vedlegg = vedleggService.hentVedlegg(receivedDialogmelding)

        val validationResult = validationService.validate(
            receivedDialogmelding = receivedDialogmelding,
            vedlegg = vedlegg,
            loggingMeta = loggingMeta,
        )

        when (validationResult.status) {
            Status.OK -> handleStatusOK(
                database = database,
                apprecService = apprecService,
                loggingMeta = loggingMeta,
                journalService = journalService,
                dialogmeldingProducer = dialogmeldingProducer,
                receivedDialogmelding = receivedDialogmelding,
                vedlegg = vedlegg,
                validationResult = validationResult,
                navnSignerendeLege = navnSignerendeLege,
            )

            Status.INVALID -> handleStatusINVALID(
                apprecService = apprecService,
                validationResult = validationResult,
                loggingMeta = loggingMeta,
                journalService = journalService,
                receivedDialogmelding = receivedDialogmelding,
                vedlegg = vedlegg,
                navnSignerendeLege = navnSignerendeLege,
            )
        }

        persistRecivedMessageValidation(
            receivedDialogmelding = receivedDialogmelding,
            validationResult = validationResult,
            database = database,
        )

        val duration = Duration.ofMillis(System.currentTimeMillis() - starttime)
        REQUEST_TIME.record(duration)

        logger.info(
            "Finished message got outcome {}, {}, processing took {} ms",
            StructuredArguments.keyValue("status", validationResult.status),
            StructuredArguments.keyValue(
                "ruleHits",
                validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }
            ),
            StructuredArguments.keyValue("latency", duration.toMillis()),
            StructuredArguments.fields(loggingMeta)
        )
    }

    companion object {
        val HENVENDELSE_DELAY: Duration = Duration.ofHours(1)
        val ALERT_DELAY: Duration = Duration.ofHours(1)

        fun processingDelay(dialogmeldingType: DialogmeldingType): Duration =
            if (dialogmeldingType == DialogmeldingType.DIALOGMELDING_HENVENDELSE_FRA_LEGE_HENDVENDELSE) {
                HENVENDELSE_DELAY
            } else {
                Duration.ZERO
            }
    }
}
