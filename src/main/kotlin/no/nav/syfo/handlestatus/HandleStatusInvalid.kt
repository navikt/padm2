package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.apprecV1.XMLCV
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.syfo.Environment
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprecCV
import no.nav.syfo.client.IdentInfoResult
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.metrics.INVALID_MESSAGE_NO_NOTICE
import no.nav.syfo.metrics.TEST_FNR_IN_PROD
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.persistering.db.domain.DialogmeldingTidspunkt
import no.nav.syfo.persistering.handleRecivedMessage
import no.nav.syfo.services.JournalService
import no.nav.syfo.services.sendReceipt
import no.nav.syfo.util.LogType
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.createLogEntry
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.jms.MessageProducer
import javax.jms.Session

suspend fun handleStatusINVALID(
    validationResult: ValidationResult,
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta,
    apprecQueueName: String,
    journalService: JournalService,
    receivedDialogmelding: ReceivedDialogmelding,
    vedleggListe: List<Vedlegg>?,
    database: DatabaseInterface,
    pasientNavn: String,
    navnSignerendeLege: String,
    sha256String: String,
) {

    journalService.onJournalRequest(
        receivedDialogmelding,
        validationResult,
        vedleggListe,
        loggingMeta,
        pasientNavn,
        navnSignerendeLege
    )

    handleRecivedMessage(receivedDialogmelding, validationResult, sha256String, loggingMeta, database)

    sendReceipt(
        session = session,
        receiptProducer = receiptProducer,
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
    logger.info("Apprec Receipt with status Avvist sent to {}, {}", apprecQueueName, fields(loggingMeta))
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

fun handlePatientNotFoundInAktorRegister(
    patientIdents: IdentInfoResult?,
    loggingMeta: LoggingMeta
): String {
    logger.warn(
        "Patient not found i aktorRegister error {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta,
            "errorMessage" to (patientIdents?.feilmelding ?: "No response for FNR"),
        )
    )
    INVALID_MESSAGE_NO_NOTICE.inc()
    return "Pasienten er ikke registrert i folkeregisteret"
}

fun handlePatientNotFound(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    env: Environment,
    loggingMeta: LoggingMeta
) {
    logger.warn(
        "Pasienten er ikke funnet i dialogmeldingen {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta
        )
    )

    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError("Pasienten er ikke funnet i dialogmeldingen")
        )
    )
    logger.info("Apprec Receipt with status Avvist sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handleDoctorNotFoundInAktorRegister(
    doctorIdents: IdentInfoResult?,
    loggingMeta: LoggingMeta
): String {
    logger.warn(
        "Behandler er ikke registrert i folkeregisteret {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta,
            "errorMessage" to (doctorIdents?.feilmelding ?: "No response for FNR")
        ),
    )
    INVALID_MESSAGE_NO_NOTICE.inc()
    return "Dialogmelding kan ikke rettes, det må skrives en ny. Grunnet følgende: Behandler er ikke registrert i folkeregisteret"
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
    return "Dialogmelding kan ikke rettes, test fødselsnummer er kommet inn i produksjon. Kontakt din EPJ-leverandør"
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
    return "Dialogmelding kan ikke rettes, meldingstekst (tekstNotatInnhold) mangler. Kontakt din EPJ-leverandør"
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
    return "Dialogmelding kan ikke rettes, det er brukt ein ugyldig dialogmelding kodeverk kombinasjon. Kontakt din EPJ-leverandør"
}

fun createApprecError(errorText: String): XMLCV = XMLCV().apply {
    dn = errorText
    v = "2.16.578.1.12.4.1.1.8221"
    s = "X99"
}
