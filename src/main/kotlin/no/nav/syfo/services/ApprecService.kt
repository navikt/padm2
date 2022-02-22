package no.nav.syfo.services

import no.nav.helse.apprecV1.XMLAppRec
import no.nav.helse.apprecV1.XMLCV
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.apprec.*
import no.nav.syfo.logger
import no.nav.syfo.metrics.APPREC_COUNTER
import no.nav.syfo.util.apprecMarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.toString

fun sendReceipt(
    mqSender: MQSenderInterface,
    fellesformat: XMLEIFellesformat,
    apprecStatus: ApprecStatus,
    apprecErrors: List<XMLCV> = listOf()
) {
    val msgHead: XMLMsgHead = fellesformat.get()
    val msgId = msgHead.msgInfo.msgId
    if (isNotTestDialogmelding(msgId)) {
        val apprec = createApprec(fellesformat, apprecStatus)
        apprec.get<XMLAppRec>().error.addAll(apprecErrors)
        mqSender.sendReceipt(
            payload = apprecMarshaller.toString(apprec)
        )
        APPREC_COUNTER.inc()
    } else {
        logger.info("Msgid $msgId is test-dialogmelding from syfomock - skipping sending of apprec")
    }
}

private fun isNotTestDialogmelding(msgId: String): Boolean =
    !isTestDialogmelding(msgId)

private fun isTestDialogmelding(msgId: String): Boolean =
    msgId.lowercase() in listOf(
        "37340d30-fe14-42b5-985f-a8ff8ffa0cb5",
        "9be88bc5-4219-473e-954b-c0dd115ff4f0",
        "b62016eb-6c2d-417a-8ecc-157b3c5ee2ca",
    )
