package no.nav.syfo.persistering

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.db.Database
import no.nav.syfo.log
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.db.erDialogmeldingOpplysningerLagret
import no.nav.syfo.persistering.db.lagreMottattDialogmelding
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
fun handleRecivedMessage(
    receivedDialogmelding: ReceivedDialogmelding,
    validationResult: ValidationResult,
    sha256String: String,
    loggingMeta: LoggingMeta,
    database: Database
) {

    if (database.connection.erDialogmeldingOpplysningerLagret(
            receivedDialogmelding.dialogmelding.id
        )
    ) {
        log.warn(
            "Dialogmelding med dialogmeldingid {}, er allerede lagret i databasen, {}",
            receivedDialogmelding.dialogmelding.id, StructuredArguments.fields(loggingMeta)
        )
    } else {
        database.lagreMottattDialogmelding(receivedDialogmelding, validationResult, sha256String)
        log.info(
            "Dialogmelding lagret i databasen, for {}",
            StructuredArguments.fields(loggingMeta)
        )
        MESSAGE_STORED_IN_DB_COUNTER.inc()
    }
}
