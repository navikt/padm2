package no.nav.syfo.services

import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.createArenaDialogNotat
import no.nav.syfo.client.isbehandlerdialog.BehandlerdialogClient
import no.nav.syfo.client.sendArenaDialogNotat
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.util.*

class ArenaDialogmeldingService(
    private val mqSender: MQSenderInterface,
    private val smtssClient: SmtssClient,
    private val emottakService: EmottakService,
    private val behandlerdialogClient: BehandlerdialogClient,
) {
    suspend fun sendArenaDialogmeldingToMQ(
        receivedDialogmelding: ReceivedDialogmelding,
        fellesformatXml: XMLEIFellesformat,
    ) {
        val msgHead: XMLMsgHead = fellesformatXml.get()
        val emottakBlokk = fellesformatXml.get<XMLMottakenhetBlokk>()
        val loggingMeta = LoggingMeta.create(
            emottakBlokk = emottakBlokk,
            fellesformatXml = fellesformatXml,
            msgHead = msgHead,
        )
        val tssId = getTssId(
            receivedDialogmelding = receivedDialogmelding,
            loggingMeta = loggingMeta,
            msgHead = msgHead,
            emottakBlokk = emottakBlokk,
        )
        val arenaDialogNotat = createArenaDialogNotat(
            fellesformat = fellesformatXml,
            tssid = tssId,
            legefnr = receivedDialogmelding.personNrLege,
            innbyggerident = receivedDialogmelding.personNrPasient,
            msgHead = msgHead,
            emottakblokk = emottakBlokk,
            dialogmelding = receivedDialogmelding.dialogmelding,
        )

        sendArenaDialogNotat(
            mqSender = mqSender,
            arenaDialogNotat = arenaDialogNotat,
            loggingMeta = loggingMeta
        )
    }

    suspend fun isMeldingStoredInBehandlerdialog(msgId: String): Boolean {
        return behandlerdialogClient.isMeldingInBehandlerdialog(msgId)
    }

    private suspend fun getTssId(
        receivedDialogmelding: ReceivedDialogmelding,
        msgHead: XMLMsgHead,
        emottakBlokk: XMLMottakenhetBlokk,
        loggingMeta: LoggingMeta,
    ): String {
        val tssId = smtssClient.findBestTss(
            legePersonIdent = PersonIdent(receivedDialogmelding.personNrLege),
            legekontorOrgName = receivedDialogmelding.legekontorOrgName,
            dialogmeldingId = receivedDialogmelding.msgId,
        )

        if (tssId != null && tssId.tssid.isNotBlank()) {
            emottakService.registerEmottakSubscription(
                tssId = tssId,
                partnerReferanse = emottakBlokk.partnerReferanse,
                sender = msgHead.msgInfo.sender,
                msgId = msgHead.msgInfo.msgId,
                loggingMeta = loggingMeta,
            )
        }

        return tssId?.tssid ?: ""
    }
}
