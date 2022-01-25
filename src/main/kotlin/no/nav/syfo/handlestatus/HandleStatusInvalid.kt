package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.apprecV1.XMLCV
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.apprec.*
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.metrics.INVALID_MESSAGE_NO_NOTICE
import no.nav.syfo.metrics.TEST_FNR_IN_PROD
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.persistering.db.domain.DialogmeldingTidspunkt
import no.nav.syfo.persistering.db.erDialogmeldingOpplysningerSendtApprec
import no.nav.syfo.persistering.db.lagreSendtApprec
import no.nav.syfo.services.JournalService
import no.nav.syfo.services.sendReceipt
import no.nav.syfo.util.LogType
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.createLogEntry
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

suspend fun handleStatusINVALID(
    database: DatabaseInterface,
    mqSender: MQSenderInterface,
    validationResult: ValidationResult,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta,
    journalService: JournalService,
    receivedDialogmelding: ReceivedDialogmelding,
    vedleggListe: List<Vedlegg>?,
    pasientNavn: String,
    navnSignerendeLege: String,
    innbyggerOK: Boolean,
) {

    if (innbyggerOK) {
        journalService.onJournalRequest(
            receivedDialogmelding,
            validationResult,
            vedleggListe,
            loggingMeta,
            pasientNavn,
            navnSignerendeLege
        )
    } else {
        logger.info("Lagrer ikke i Joark siden pasient ikke funnet {}", fields(loggingMeta))
    }

    if (!database.erDialogmeldingOpplysningerSendtApprec(receivedDialogmelding.dialogmelding.id)) {
        sendReceipt(
            mqSender = mqSender,
            fellesformat = fellesformat,
            apprecStatus = ApprecStatus.avvist,
            apprecErrors = run {
                val errors = mutableListOf<XMLCV>()
                validationResult.apprecMessage?.let {
                    errors.add(createApprecError(it))
                }
                errors.addAll(validationResult.ruleHits.map { it.toApprecCV() })
                errors
            }
        )
        logger.info("Apprec Receipt with status Avvist sent, {}", fields(loggingMeta))
        database.lagreSendtApprec(receivedDialogmelding.dialogmelding.id)
    }
}

fun handleDuplicateDialogmeldingContent(
    loggingMeta: LoggingMeta,
    sha256String: String,
    opprinneligMeldingTidspunkt: DialogmeldingTidspunkt,
): String {
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

    INVALID_MESSAGE_NO_NOTICE.inc()
    return "Duplikat! - Dialogmeldingen fra $tidMottattNyMelding har vi tidligere mottatt den $tidMottattOpprinneligMelding. Skal ikke sendes på nytt."
}

fun handlePatientNotFound(
    loggingMeta: LoggingMeta
): String {
    logger.warn(
        "Patient not found in PDL error {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta,
            "errorMessage" to "No response for FNR",
        )
    )
    INVALID_MESSAGE_NO_NOTICE.inc()
    return "Dialogmeldingen er ikke gyldig. Pasienten er ikke registrert i folkeregisteret."
}

fun handlePatientMissing(
    mqSender: MQSenderInterface,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta
) {
    logger.warn(
        "Pasienten er ikke funnet i dialogmeldingen eller fnr er ugyldig {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta
        )
    )

    sendReceipt(
        mqSender, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError("Pasienten er ikke funnet i dialogmeldingen eller fnr er ugyldig")
        )
    )
    logger.info("Apprec Receipt with status Avvist sent, {}", fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handleBehandlerNotFound(
    loggingMeta: LoggingMeta
): String {
    logger.warn(
        "Behandler er ikke registrert i folkeregisteret {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta,
            "errorMessage" to "No response for FNR"
        ),
    )
    INVALID_MESSAGE_NO_NOTICE.inc()
    return "Dialogmeldingen er ikke gyldig. Behandler er ikke registrert i folkeregisteret."
}

fun handleTestFnrInProd(
    loggingMeta: LoggingMeta
): String {
    logger.warn(
        "Test fødselsnummer er kommet inn i produksjon {}",
        createLogEntry(
            LogType.TEST_FNR_IN_PRODUCTION,
            loggingMeta
        )
    )

    INVALID_MESSAGE_NO_NOTICE.inc()
    TEST_FNR_IN_PROD.inc()
    return "Dialogmeldingen er ikke gyldig. Fødselsnummer er fra testmiljøet. Kontakt din EPJ-leverandør."
}

fun handleMeldingsTekstMangler(
    loggingMeta: LoggingMeta
): String {

    logger.warn(
        "TekstNotatInnhold mangler {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta
        )
    )
    INVALID_MESSAGE_NO_NOTICE.inc()
    return "Dialogmeldingen er ikke gyldig: meldingstekst mangler."
}

fun handleInvalidDialogMeldingKodeverk(
    loggingMeta: LoggingMeta
): String {

    logger.warn(
        "Det er brukt ein ugyldig kombinasjon av dialogmelding kodeverk {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta
        )
    )
    INVALID_MESSAGE_NO_NOTICE.inc()
    return "Dialogmeldingen er ikke gyldig: meldingstypen stemmer ikke med innholdet. Kontakt din EPJ-leverandør."
}

fun createApprecError(errorText: String): XMLCV = XMLCV().apply {
    dn = errorText
    v = "X99"
    s = "2.16.578.1.12.4.1.1.8221"
}
