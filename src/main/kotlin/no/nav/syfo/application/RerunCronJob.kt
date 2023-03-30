package no.nav.syfo.application

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.metrics.MESSAGES_STILL_FAIL_AFTER_1H
import no.nav.syfo.persistering.db.hentIkkeFullforteDialogmeldinger
import java.time.LocalDateTime

class RerunCronJob(
    val database: DatabaseInterface,
    val dialogmeldingProcessor: DialogmeldingProcessor,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val result = CronjobResult()
        database.hentIkkeFullforteDialogmeldinger().forEach { (dialogmeldingId, fellesformat, mottattDatetime) ->
            try {
                logger.info("Attempting reprocessing of $dialogmeldingId")
                dialogmeldingProcessor.processMessage(dialogmeldingId, fellesformat)
                result.updated++
            } catch (e: Exception) {
                logger.warn("Exception caught while reprocessing message, will try again later: ${e.message}", e)
                result.failed++
                if (mottattDatetime.isBefore(LocalDateTime.now().minusHours(1))) {
                    MESSAGES_STILL_FAIL_AFTER_1H.inc()
                }
            }
        }
        logger.info(
            "Completed rerun cron job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }
}

data class CronjobResult(
    var updated: Int = 0,
    var failed: Int = 0
)
