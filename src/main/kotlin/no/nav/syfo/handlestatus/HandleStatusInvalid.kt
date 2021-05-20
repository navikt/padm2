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
import no.nav.syfo.persistering.handleRecivedMessage
import no.nav.syfo.services.JournalService
import no.nav.syfo.services.sendReceipt
import no.nav.syfo.services.updateRedis
import no.nav.syfo.util.LogType
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.createLogEntry
import redis.clients.jedis.Jedis
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

    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.avvist,
        validationResult.ruleHits.map { it.toApprecCV() })
    log.info("Apprec Receipt sent to {}, {}", apprecQueueName, fields(loggingMeta))
}

fun handleDuplicateSM2013Content(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta,
    env: Environment,
    redisSha256String: String
) {

    log.warn(
        "Duplicate message: Same redisSha256String {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta,
            "originalEdiLoggId" to redisSha256String,
        )
    )

    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
            createApprecError(
                "Duplikat! - Denne dialogmeldingen er mottatt tidligere. " +
                        "Skal ikke sendes på nytt."
            )
        )
    )
    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handleDuplicateEdiloggid(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta,
    env: Environment,
    redisEdiloggid: String
) {

    log.warn(
        "Duplicate message: Same redisEdiloggid {}",
        createLogEntry(
            LogType.INVALID_MESSAGE,
            loggingMeta,
            "originalEdiLoggId" to redisEdiloggid,
        )
    )

    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
            createApprecError(
                "Dialogmeldingen kan ikke rettes, det må skrives en ny. Grunnet følgende:" +
                        "Denne dialogmeldingen har ein identisk identifikator med ein dialogmeldingen som er mottatt tidligere," +
                        " og er derfor ein duplikat." +
                        " og skal ikke sendes på nytt. Dersom dette ikke stemmer, kontakt din EPJ-leverandør"
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
    ediLoggId: String,
    jedis: Jedis,
    sha256String: String,
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
        session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
            createApprecError("Pasienten er ikke registrert i folkeregisteret")
        )
    )

    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
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
        session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
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
    ediLoggId: String,
    jedis: Jedis,
    sha256String: String,
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
        session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
            createApprecError(
                "Dialogmelding kan ikke rettes, det må skrives en ny. Grunnet følgende:" +
                        " Behandler er ikke registrert i folkeregisteret"
            )
        )
    )

    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun handleTestFnrInProd(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    jedis: Jedis,
    sha256String: String,
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
        session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
            createApprecError(
                "Dialogmelding kan ikke rettes, test fødselsnummer er kommet inn i produksjon." +
                        "Kontakt din EPJ-leverandør)"
            )
        )
    )
    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
    TEST_FNR_IN_PROD.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun handleMeldingsTekstMangler(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    jedis: Jedis,
    sha256String: String,
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
        session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
            createApprecError(
                "Dialogmelding kan ikke rettes, meldingstekst (tekstNotatInnhold) mangler, " +
                        "Kontakt din EPJ-leverandør)"
            )
        )
    )
    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun handleInvalidDialogMeldingKodeverk(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    jedis: Jedis,
    sha256String: String,
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
        session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
            createApprecError(
                "Dialogmelding kan ikke rettes, det er brukt ein ugyldig dialogmelding kodeverk kombinasjon, " +
                        "Kontakt din EPJ-leverandør)"
            )
        )
    )
    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun createApprecError(textToTreater: String): XMLCV = XMLCV().apply {
    dn = textToTreater
    v = "2.16.578.1.12.4.1.1.8221"
    s = "X99"
}
