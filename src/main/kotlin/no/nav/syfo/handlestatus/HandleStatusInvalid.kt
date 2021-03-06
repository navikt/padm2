package no.nav.syfo.handlestatus

import io.ktor.util.*
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.apprecV1.XMLCV
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.syfo.Environment
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprecCV
import no.nav.syfo.client.IdentInfoResult
import no.nav.syfo.db.Database
import no.nav.syfo.log
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

@KtorExperimentalAPI
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
    database: Database,
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
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        validationResult.ruleHits.map { it.toApprecCV() }
    )
    log.info("Apprec Receipt sent to {}, {}", apprecQueueName, fields(loggingMeta))
}

fun handleDuplicateDialogmeldingContent(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta,
    env: Environment,
    sha256String: String,
    opprinneligMeldingTidspunkt: DialogmeldingTidspunkt,
) {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    val tidMottattOpprinneligMelding = opprinneligMeldingTidspunkt.mottattTidspunkt.format(formatter)
    val tidMottattNyMelding = LocalDateTime.now().format(formatter)

    log.warn(
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

    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError(
                "Duplikat! - Dialogmeldingen fra $tidMottattNyMelding har vi tidligere mottatt den $tidMottattOpprinneligMelding. Skal ikke sendes på nytt."
            )
        )
    )
    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handlePatientNotFoundInAktorRegister(
    patientIdents: IdentInfoResult?,
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    env: Environment,
    loggingMeta: LoggingMeta
) {
    log.warn(
        "Patient not found i aktorRegister error {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta,
            "errorMessage" to (patientIdents?.feilmelding ?: "No response for FNR"),
        )
    )

    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError("Pasienten er ikke registrert i folkeregisteret")
        )
    )

    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handlePatientNotFound(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    env: Environment,
    loggingMeta: LoggingMeta
) {
    log.warn(
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

    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handleDoctorNotFoundInAktorRegister(
    doctorIdents: IdentInfoResult?,
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    env: Environment,
    loggingMeta: LoggingMeta
) {
    log.warn(
        "Behandler er ikke registrert i folkeregisteret {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta,
            "errorMessage" to (doctorIdents?.feilmelding ?: "No response for FNR")
        ),
    )

    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError(
                "Dialogmelding kan ikke rettes, det må skrives en ny. Grunnet følgende:" +
                    " Behandler er ikke registrert i folkeregisteret"
            )
        )
    )

    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handleTestFnrInProd(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    env: Environment,
    loggingMeta: LoggingMeta
) {
    log.warn(
        "Test fødselsnummer er kommet inn i produksjon {}",
        createLogEntry(
            LogType.TEST_FNR_IN_PRODUCTION,
            loggingMeta
        )
    )

    log.warn(
        "Avsender fodselsnummer er registert i Helsepersonellregisteret (HPR) {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta
        )
    )

    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError(
                "Dialogmelding kan ikke rettes, test fødselsnummer er kommet inn i produksjon." +
                    "Kontakt din EPJ-leverandør)"
            )
        )
    )
    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
    TEST_FNR_IN_PROD.inc()
}

fun handleMeldingsTekstMangler(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    env: Environment,
    loggingMeta: LoggingMeta
) {

    log.warn(
        "TekstNotatInnhold mangler {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta
        )
    )

    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError(
                "Dialogmelding kan ikke rettes, meldingstekst (tekstNotatInnhold) mangler, " +
                    "Kontakt din EPJ-leverandør)"
            )
        )
    )
    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handleInvalidDialogMeldingKodeverk(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    env: Environment,
    loggingMeta: LoggingMeta
) {

    log.warn(
        "Det er brukt ein ugyldig kombinasjon av dialogmelding kodeverk {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta
        )
    )

    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError(
                "Dialogmelding kan ikke rettes, det er brukt ein ugyldig dialogmelding kodeverk kombinasjon, " +
                    "Kontakt din EPJ-leverandør)"
            )
        )
    )
    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun createApprecError(textToTreater: String): XMLCV = XMLCV().apply {
    dn = textToTreater
    v = "2.16.578.1.12.4.1.1.8221"
    s = "X99"
}
