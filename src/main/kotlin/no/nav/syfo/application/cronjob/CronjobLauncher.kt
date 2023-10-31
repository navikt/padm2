package no.nav.syfo.application.cronjob

import no.nav.syfo.application.*
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.services.ArenaDialogmeldingService

fun launchCronjobs(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    dialogmeldingProcessor: DialogmeldingProcessor,
    arenaDialogmeldingService: ArenaDialogmeldingService,
) {
    val cronjobRunner = CronjobRunner(applicationState)

    val rerunCronJob = RerunCronJob(
        database = database,
        dialogmeldingProcessor = dialogmeldingProcessor,
    )
    val sendDialogmeldingArenaCronjob = SendDialogmeldingArenaCronjob(
        database = database,
        arenaDialogmeldingService = arenaDialogmeldingService,
    )
    val allCronjobs = mutableListOf(
        rerunCronJob,
        sendDialogmeldingArenaCronjob,
    )

    allCronjobs.forEach {
        launchBackgroundTask(applicationState) {
            cronjobRunner.start(cronjob = it)
        }
    }
}
