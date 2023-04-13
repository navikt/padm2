package no.nav.syfo.services

import no.nav.helse.apprecV1.XMLAppRec
import no.nav.helse.apprecV1.XMLCV
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.createApprec
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
    if (isTestDialogmelding(msgId)) {
        logger.info("Msgid $msgId is test-dialogmelding from syfomock - skipping sending of apprec")
    } else {
        val apprec = createApprec(fellesformat, apprecStatus)
        apprec.get<XMLAppRec>().error.addAll(apprecErrors)
        mqSender.sendReceipt(
            payload = apprecMarshaller.toString(apprec)
        )
        APPREC_COUNTER.increment()
    }
}

private fun isTestDialogmelding(msgId: String): Boolean =
    msgId.startsWith("syfomock-")
