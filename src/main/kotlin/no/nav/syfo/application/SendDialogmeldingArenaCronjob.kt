package no.nav.syfo.application

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.persistering.db.getUnpublishedArenaMeldinger
import no.nav.syfo.persistering.db.lagreSendtArena
import no.nav.syfo.services.ArenaDialogmeldingService
import no.nav.syfo.util.safeUnmarshal
import org.slf4j.LoggerFactory

class SendDialogmeldingArenaCronjob(
    private val database: DatabaseInterface,
    private val arenaDialogmeldingService: ArenaDialogmeldingService,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed sending dialogmelding to arena with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    suspend fun runJob(): CronjobResult {
        val result = CronjobResult()
        val unpublishedArenaMeldinger = database.getUnpublishedArenaMeldinger()
        unpublishedArenaMeldinger.forEach { (dialogmeldingId, fellesformat, msgId) ->
            try {
                if (!arenaDialogmeldingService.isMeldingStoredInModia(msgId)) {
                    val fellesformatXml = safeUnmarshal(fellesformat)
                    val receivedDialogmelding = ReceivedDialogmelding.create(
                        dialogmeldingId = dialogmeldingId,
                        fellesformat = fellesformatXml,
                        inputMessageText = fellesformat,
                    )
                    arenaDialogmeldingService.sendArenaDialogmeldingToMQ(
                        receivedDialogmelding = receivedDialogmelding,
                        fellesformatXml = fellesformatXml
                    )
                    database.lagreSendtArena(
                        dialogmeldingid = dialogmeldingId,
                        isSent = true,
                    )
                    result.updated++
                }
            } catch (e: Exception) {
                log.error("Caught exception in sending dialogmelding to arena", e)
                result.failed++
            }
        }

        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(SendDialogmeldingArenaCronjob::class.java)
    }
}
