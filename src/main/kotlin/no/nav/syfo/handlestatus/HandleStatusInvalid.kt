package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.logger
import no.nav.syfo.metrics.INVALID_MESSAGE_NO_NOTICE
import no.nav.syfo.metrics.TEST_FNR_IN_PROD
import no.nav.syfo.model.*
import no.nav.syfo.persistering.db.domain.DialogmeldingTidspunkt
import no.nav.syfo.services.ApprecService
import no.nav.syfo.services.JournalService
import no.nav.syfo.util.LogType
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.createLogEntry
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val RULE_NAME_DUPLICATE = "DUPLICATE_DIALOGMELDING_CONTENT"
val RULE_NAME_VIRUS_CHECK = "VIRUSSJEKK_FEILET"

suspend fun handleStatusINVALID(
    apprecService: ApprecService,
    validationResult: ValidationResult,
    loggingMeta: LoggingMeta,
    journalService: JournalService,
    receivedDialogmelding: ReceivedDialogmelding,
    vedleggListe: List<Vedlegg>?,
    pasientNavn: String,
    navnSignerendeLege: String,
    innbyggerOK: Boolean,
) {
    if (innbyggerOK && !validationResult.isVirusCheck() && !validationResult.isDuplicate()) {
        journalService.onJournalRequest(
            receivedDialogmelding,
            validationResult,
            vedleggListe,
            loggingMeta,
            pasientNavn,
            navnSignerendeLege
        )
    } else {
        logger.info(
            if (validationResult.isDuplicate()) {
                "Lagrer ikke i Joark siden meldingen er en duplikat dialogmelding {}"
            } else if (validationResult.isVirusCheck()) {
                "Lagrer ikke i Joark siden meldingen feilet på virussjekk"
            } else {
                "Lagrer ikke i Joark siden pasient ikke funnet {}"
            },
            fields(loggingMeta)
        )
    }

    apprecService.ferdigstillAvvist(receivedDialogmelding, validationResult, loggingMeta)
}

fun handleDuplicateDialogmeldingContent(
    loggingMeta: LoggingMeta,
    sha256String: String,
    opprinneligMeldingTidspunkt: DialogmeldingTidspunkt,
): ValidationResult {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    val tidMottattOpprinneligMelding = opprinneligMeldingTidspunkt.mottattTidspunkt.format(formatter)
    val tidMottattNyMelding = LocalDateTime.now().format(formatter)

    logger.warn(
        "Duplicate message: Same sha256String {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta,
            "shaString" to sha256String,
            "tidSignertOpprinneligMelding" to opprinneligMeldingTidspunkt.signaturDato,
            "tidMottattOpprinneligMelding" to tidMottattOpprinneligMelding,
            "tidMottattNyMelding" to tidMottattNyMelding
        )
    )

    INVALID_MESSAGE_NO_NOTICE.increment()

    val message = "Duplikat! - Dialogmeldingen fra $tidMottattNyMelding har vi tidligere mottatt den $tidMottattOpprinneligMelding. Skal ikke sendes på nytt."
    return ValidationResult(
        status = Status.INVALID,
        ruleHits = listOf(
            RuleInfo(
                ruleName = RULE_NAME_DUPLICATE,
                messageForSender = message,
                messageForUser = message,
                ruleStatus = Status.INVALID
            )
        )
    )
}

fun handlePatientNotFound(
    loggingMeta: LoggingMeta
): ValidationResult {
    logger.warn(
        "Patient not found in PDL error {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta,
            "errorMessage" to "No response for FNR",
        )
    )
    INVALID_MESSAGE_NO_NOTICE.increment()

    val message = "Dialogmeldingen er ikke gyldig. Pasienten er ikke registrert i folkeregisteret."
    return ValidationResult(
        status = Status.INVALID,
        ruleHits = listOf(
            RuleInfo(
                ruleName = "PATIENT_NOT_FOUND",
                messageForSender = message,
                messageForUser = message,
                ruleStatus = Status.INVALID
            )
        )
    )
}

fun handlePatientMissing(
    loggingMeta: LoggingMeta
) {
    logger.warn(
        "Pasienten er ikke funnet i dialogmeldingen eller fnr er ugyldig {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta
        )
    )
    INVALID_MESSAGE_NO_NOTICE.increment()
}

fun handleBehandlerNotFound(
    loggingMeta: LoggingMeta
): ValidationResult {
    logger.warn(
        "Behandler er ikke registrert i folkeregisteret {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta,
            "errorMessage" to "No response for FNR"
        ),
    )
    INVALID_MESSAGE_NO_NOTICE.increment()

    val message = "Dialogmeldingen er ikke gyldig. Behandler er ikke registrert i folkeregisteret."
    return ValidationResult(
        status = Status.INVALID,
        ruleHits = listOf(
            RuleInfo(
                ruleName = "BEHANDLER_NOT_FOUND",
                messageForSender = message,
                messageForUser = message,
                ruleStatus = Status.INVALID
            )
        )
    )
}

fun handleTestFnrInProd(
    loggingMeta: LoggingMeta
): ValidationResult {
    logger.warn(
        "Test fødselsnummer er kommet inn i produksjon {}",
        createLogEntry(
            LogType.TEST_FNR_IN_PRODUCTION,
            loggingMeta
        )
    )

    INVALID_MESSAGE_NO_NOTICE.increment()
    TEST_FNR_IN_PROD.increment()

    val message = "Dialogmeldingen er ikke gyldig. Fødselsnummer er fra testmiljøet. Kontakt din EPJ-leverandør."
    return ValidationResult(
        status = Status.INVALID,
        ruleHits = listOf(
            RuleInfo(
                ruleName = "TEST_FNR_IN_PROD",
                messageForSender = message,
                messageForUser = message,
                ruleStatus = Status.INVALID
            )
        )
    )
}

fun handleMeldingsTekstMangler(
    loggingMeta: LoggingMeta
): ValidationResult {
    logger.warn(
        "TekstNotatInnhold mangler {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta
        )
    )
    INVALID_MESSAGE_NO_NOTICE.increment()

    val message = "Dialogmeldingen er ikke gyldig: meldingstekst mangler."
    return ValidationResult(
        status = Status.INVALID,
        ruleHits = listOf(
            RuleInfo(
                ruleName = "MELDINGSTEKST_MANGLER",
                messageForSender = message,
                messageForUser = message,
                ruleStatus = Status.INVALID
            )
        )
    )
}

fun handleInvalidDialogMeldingKodeverk(
    loggingMeta: LoggingMeta
): ValidationResult {
    logger.warn(
        "Det er brukt ein ugyldig kombinasjon av dialogmelding kodeverk {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta
        )
    )
    INVALID_MESSAGE_NO_NOTICE.increment()

    val message = "Dialogmeldingen er ikke gyldig: meldingstypen stemmer ikke med innholdet. Kontakt din EPJ-leverandør."
    return ValidationResult(
        status = Status.INVALID,
        ruleHits = listOf(
            RuleInfo(
                ruleName = "INVALID_KODEVERK",
                messageForSender = message,
                messageForUser = message,
                ruleStatus = Status.INVALID
            )
        )
    )
}

fun handleVedleggMayContainVirus(
    loggingMeta: LoggingMeta
): ValidationResult {
    logger.warn(
        "Et eller flere vedlegg feilet i virussjekk {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta
        )
    )
    INVALID_MESSAGE_NO_NOTICE.increment()

    val message = "Dialogmeldingen er ikke gyldig: Et eller flere vedlegg feilet i virussjekk."
    return ValidationResult(
        status = Status.INVALID,
        ruleHits = listOf(
            RuleInfo(
                ruleName = RULE_NAME_VIRUS_CHECK,
                messageForSender = message,
                messageForUser = message,
                ruleStatus = Status.INVALID
            )
        )
    )
}
