package no.nav.syfo.persistering

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.db.erDialogmeldingOpplysningerLagret
import no.nav.syfo.persistering.db.lagreMottattDialogmelding
import no.nav.syfo.util.LoggingMeta

fun handleRecivedMessage(
    receivedDialogmelding: ReceivedDialogmelding,
    validationResult: ValidationResult,
    sha256String: String,
    loggingMeta: LoggingMeta,
    database: DatabaseInterface,
) {

    if (database.connection.erDialogmeldingOpplysningerLagret(
            receivedDialogmelding.dialogmelding.id
        )
    ) {
        logger.warn(
            "Dialogmelding med dialogmeldingid {}, er allerede lagret i databasen, {}",
            receivedDialogmelding.dialogmelding.id, StructuredArguments.fields(loggingMeta)
        )
    } else {
        database.lagreMottattDialogmelding(receivedDialogmelding, validationResult, sha256String)
        logger.info(
            "Dialogmelding lagret i databasen, for {}",
            StructuredArguments.fields(loggingMeta)
        )
        MESSAGE_STORED_IN_DB_COUNTER.inc()
    }
}
