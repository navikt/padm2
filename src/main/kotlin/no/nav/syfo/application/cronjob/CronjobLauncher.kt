package no.nav.syfo.application.cronjob

import no.nav.syfo.Environment
import no.nav.syfo.application.*
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.services.ArenaDialogmeldingService

fun launchCronjobs(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    dialogmeldingProcessor: DialogmeldingProcessor,
    arenaDialogmeldingService: ArenaDialogmeldingService,
) {
    val cronjobRunner = CronjobRunner(applicationState)

    val rerunCronJob = RerunCronJob(
        database = database,
        dialogmeldingProcessor = dialogmeldingProcessor,
    )

    val allCronjobs = mutableListOf<Cronjob>(
        rerunCronJob,
    )

    if (environment.useCronjobToPublishToArena) {
        val sendDialogmeldingArenaCronjob = SendDialogmeldingArenaCronjob(
            database = database,
            arenaDialogmeldingService = arenaDialogmeldingService,
        )
        allCronjobs.add(sendDialogmeldingArenaCronjob)
    }
    // TODO: Remove after it has run once in prod
    val sendDialogmeldingArenaAdhocCronjob = SendDialogmeldingArenaAdhocCronjob(
        database = database,
        arenaDialogmeldingService = arenaDialogmeldingService,
    )
    allCronjobs.add(sendDialogmeldingArenaAdhocCronjob)

    allCronjobs.forEach {
        launchBackgroundTask(applicationState) {
            cronjobRunner.start(cronjob = it)
        }
    }
}
