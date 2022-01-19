package no.nav.syfo.application

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.LoggerFactory
import java.time.Duration

class CronjobRunner(
    private val applicationState: ApplicationState,
) {

    private val log = LoggerFactory.getLogger(CronjobRunner::class.java)

    suspend fun start(cronjob: Cronjob) = coroutineScope {
        val (initialDelay, intervalDelay) = delays(cronjob)
        log.info(
            "Scheduling start of ${cronjob.javaClass.simpleName}: {} ms, {} ms",
            StructuredArguments.keyValue("initialDelay", initialDelay),
            StructuredArguments.keyValue("intervalDelay", intervalDelay),
        )
        delay(initialDelay)

        while (applicationState.ready) {
            val job = launch { run(cronjob) }
            delay(intervalDelay)
            if (job.isActive) {
                log.info("Waiting for job to finish")
                job.join()
            }
        }
        log.info("Ending ${cronjob.javaClass.simpleName} due to failed readiness check ")
    }

    private suspend fun run(cronjob: Cronjob) {
        try {
            cronjob.run()
        } catch (ex: Exception) {
            log.error("Exception in ${cronjob.javaClass.simpleName}. Job will run again after delay.", ex)
        }
    }

    private fun delays(cronjob: Cronjob): Pair<Long, Long> {
        val initialDelay = Duration.ofMinutes(cronjob.initialDelayMinutes).toMillis()
        val intervalDelay = Duration.ofMinutes(cronjob.intervalDelayMinutes).toMillis()
        return Pair(initialDelay, intervalDelay)
    }
}
