package no.nav.syfo.application

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.persistering.db.hentIkkeFullforteDialogmeldinger

class RerunCronJob(
    val database: DatabaseInterface,
    val blockingApplicationRunner: BlockingApplicationRunner,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        database.hentIkkeFullforteDialogmeldinger().forEach { (dialogmeldingId, fellesformat) ->
            try {
                logger.info("Attempting reprocessing of $dialogmeldingId")
                blockingApplicationRunner.processMessage(dialogmeldingId, fellesformat)
            } catch (e: Exception) {
                logger.warn("Exception caught while reprocessing message, will try again later: {}", e.message)
            }
        }
    }
}
