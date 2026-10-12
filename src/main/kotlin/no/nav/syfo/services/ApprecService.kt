package no.nav.syfo.services

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.apprecV1.XMLAppRec
import no.nav.helse.apprecV1.XMLCV
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.PATIENT_MISSING_MESSAGE
import no.nav.syfo.apprec.createApprec
import no.nav.syfo.apprec.createApprecError
import no.nav.syfo.apprec.toApprecCV
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.metrics.APPREC_COUNTER
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.db.erFerdigstilt
import no.nav.syfo.persistering.db.lagreFerdigstilt
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.get
import no.nav.syfo.util.getApprecMarshaller
import no.nav.syfo.util.safeUnmarshal
import no.nav.syfo.util.toString

interface ApprecService {
    fun ferdigstillOk(receivedDialogmelding: ReceivedDialogmelding, loggingMeta: LoggingMeta)

    fun ferdigstillAvvist(
        receivedDialogmelding: ReceivedDialogmelding,
        validationResult: ValidationResult,
        loggingMeta: LoggingMeta,
    )

    /**
     * Avviser en melding som ikke er lagret, fordi pasienten mangler eller har ugyldig fnr.
     */
    fun avvisPasientMangler(fellesformat: XMLEIFellesformat, loggingMeta: LoggingMeta)
}

class MqApprecService(
    private val database: DatabaseInterface,
    private val mqSender: MQSenderInterface,
) : ApprecService {
    override fun ferdigstillOk(receivedDialogmelding: ReceivedDialogmelding, loggingMeta: LoggingMeta) {
        val id = receivedDialogmelding.dialogmelding.id
        if (!database.erFerdigstilt(id)) {
            sendReceipt(safeUnmarshal(receivedDialogmelding.fellesformat), ApprecStatus.OK)
            logger.info("Apprec Receipt with status OK sent, {}", StructuredArguments.fields(loggingMeta))
            database.lagreFerdigstilt(id)
        }
    }

    override fun ferdigstillAvvist(
        receivedDialogmelding: ReceivedDialogmelding,
        validationResult: ValidationResult,
        loggingMeta: LoggingMeta,
    ) {
        val id = receivedDialogmelding.dialogmelding.id
        if (!database.erFerdigstilt(id)) {
            sendReceipt(
                fellesformat = safeUnmarshal(receivedDialogmelding.fellesformat),
                apprecStatus = ApprecStatus.AVVIST,
                apprecErrors = validationResult.ruleHits.map { it.toApprecCV() },
            )
            logger.info("Apprec Receipt with status Avvist sent, {}", StructuredArguments.fields(loggingMeta))
            database.lagreFerdigstilt(id)
        }
    }

    override fun avvisPasientMangler(fellesformat: XMLEIFellesformat, loggingMeta: LoggingMeta) {
        sendReceipt(
            fellesformat = fellesformat,
            apprecStatus = ApprecStatus.AVVIST,
            apprecErrors = listOf(createApprecError(PATIENT_MISSING_MESSAGE)),
        )
        logger.info("Apprec Receipt with status Avvist sent, {}", StructuredArguments.fields(loggingMeta))
    }

    private fun sendReceipt(
        fellesformat: XMLEIFellesformat,
        apprecStatus: ApprecStatus,
        apprecErrors: List<XMLCV> = listOf(),
    ) {
        val apprec = createApprec(fellesformat, apprecStatus)
        apprec.get<XMLAppRec>().error.addAll(apprecErrors)
        mqSender.sendReceipt(
            payload = getApprecMarshaller().toString(apprec)
        )
        APPREC_COUNTER.increment()
    }
}

class NoOpApprecService(
    private val database: DatabaseInterface,
) : ApprecService {
    override fun ferdigstillOk(receivedDialogmelding: ReceivedDialogmelding, loggingMeta: LoggingMeta) {
        ferdigstill(receivedDialogmelding)
    }

    override fun ferdigstillAvvist(
        receivedDialogmelding: ReceivedDialogmelding,
        validationResult: ValidationResult,
        loggingMeta: LoggingMeta,
    ) {
        ferdigstill(receivedDialogmelding)
    }

    override fun avvisPasientMangler(fellesformat: XMLEIFellesformat, loggingMeta: LoggingMeta) {
        // Team Helsemelding sender apprec for meldinger fra Kafka.
    }

    private fun ferdigstill(receivedDialogmelding: ReceivedDialogmelding) {
        val id = receivedDialogmelding.dialogmelding.id
        if (!database.erFerdigstilt(id)) {
            database.lagreFerdigstilt(id)
        }
    }
}
