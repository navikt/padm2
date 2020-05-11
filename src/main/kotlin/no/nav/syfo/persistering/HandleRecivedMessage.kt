package no.nav.syfo.persistering

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.db.Database
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
fun handleRecivedMessage(
    receivedDialogmelding: ReceivedDialogmelding,
    validationResult: ValidationResult,
    loggingMeta: LoggingMeta,
    database: Database
) {
/*
    if (database.connection.erDialogmeldingOpplysningerLagret(
            receivedDialogmelding.dialogmelding.id
        )
    ) {
        log.warn(
            "Dialogmelding med dialogmledingid {}, er allerede lagret i databasen, {}",
            receivedDialogmelding.dialogmelding.id, StructuredArguments.fields(loggingMeta)
        )
    } else {
        database.lagreMottattDialogmelding(receivedDialogmelding, validationResult)
        log.info(
            "Dialogmelding lagret i databasen, for {}",
            StructuredArguments.fields(loggingMeta)
        )
        MESSAGE_STORED_IN_DB_COUNTER.inc()
    }

 */
}
