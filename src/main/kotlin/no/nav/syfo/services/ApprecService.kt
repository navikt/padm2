package no.nav.syfo.services

import no.nav.helse.apprecV1.XMLAppRec
import no.nav.helse.apprecV1.XMLCV
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.createApprec
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
    val apprec = createApprec(fellesformat, apprecStatus)
    apprec.get<XMLAppRec>().error.addAll(apprecErrors)
    mqSender.sendReceipt(
        payload = apprecMarshaller.toString(apprec)
    )
    APPREC_COUNTER.inc()
}
