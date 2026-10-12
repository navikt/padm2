package no.nav.syfo.application

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.metrics.MESSAGES_STILL_FAIL_AFTER_1H
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.findDialogmeldingType
import no.nav.syfo.persistering.db.hentIkkeFullforteDialogmeldinger
import no.nav.syfo.util.get
import no.nav.syfo.util.safeUnmarshal
import java.time.Duration
import java.time.LocalDateTime

class RerunCronJob(
    val database: DatabaseInterface,
    val dialogmeldingProcessor: DialogmeldingProcessor,
) : Cronjob {
    override val initialDelayMinutes: Long = 7
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val result = CronjobResult()
        database.hentIkkeFullforteDialogmeldinger().forEach { (dialogmeldingId, fellesformat, mottattDatetime) ->
            try {
                logger.info("Attempting reprocessing of $dialogmeldingId")
                val receivedDialogmelding = ReceivedDialogmelding.create(
                    dialogmeldingId = dialogmeldingId,
                    fellesformat = safeUnmarshal(fellesformat),
                    inputMessageText = fellesformat,
                )
                dialogmeldingProcessor.process(receivedDialogmelding)
                result.updated++
            } catch (e: Exception) {
                logger.warn("Exception caught while reprocessing message, will try again later: ${e.message}", e)
                result.failed++
                val alertDelay = DialogmeldingProcessor.ALERT_DELAY
                val processingDelay = processingDelay(fellesformat)
                if (mottattDatetime.isBefore(LocalDateTime.now().minus(alertDelay + processingDelay))) {
                    MESSAGES_STILL_FAIL_AFTER_1H.increment()
                }
            }
        }
        logger.info(
            "Completed rerun cron job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    private fun processingDelay(inputMessageText: String): Duration =
        try {
            val emottakblokk = safeUnmarshal(inputMessageText).get<XMLMottakenhetBlokk>()
            DialogmeldingProcessor.processingDelay(findDialogmeldingType(emottakblokk.ebService, emottakblokk.ebAction))
        } catch (e: Exception) {
            Duration.ZERO
        }
}
