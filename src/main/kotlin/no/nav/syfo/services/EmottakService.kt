package no.nav.syfo.services

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.msgHead.XMLSender
import no.nav.syfo.client.SmgcpClient
import no.nav.syfo.client.TssId
import no.nav.syfo.logger
import no.nav.syfo.metrics.DONT_UPDATE_EMOTTAK_MISSING_PARTNERREF
import no.nav.syfo.util.LoggingMeta

class EmottakService(
    private val smgcpClient: SmgcpClient
) {

    suspend fun registerEmottakSubscription(
        tssId: TssId,
        partnerReferanse: String?,
        sender: XMLSender,
        msgId: String,
        loggingMeta: LoggingMeta,
    ) {
        if (!partnerReferanse.isNullOrBlank()) {
            smgcpClient.startSubscription(
                tssId,
                sender,
                partnerReferanse.toInt(),
                msgId,
            )
        } else {
            logger.info(
                "Cannot find PartnerReferanse, subscription_emottak is not created, {}",
                StructuredArguments.fields(loggingMeta),
            )
            DONT_UPDATE_EMOTTAK_MISSING_PARTNERREF.increment()
        }
    }
}
