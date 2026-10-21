package no.nav.syfo.services

import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.SyfohelsenettproxyClient
import no.nav.syfo.client.TssId
import no.nav.syfo.client.aapapi.AapApiClient
import no.nav.syfo.client.createArenaDialogNotat
import no.nav.syfo.client.isbehandlerdialog.BehandlerdialogClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.sendArenaDialogNotat
import no.nav.syfo.client.toBehandler
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.util.*

class ArenaDialogmeldingService(
    private val mqSender: MQSenderInterface,
    private val smtssClient: SmtssClient,
    private val emottakService: EmottakService,
    private val behandlerdialogClient: BehandlerdialogClient,
    private val aapApiClient: AapApiClient,
    private val syfohelsenettproxyClient: SyfohelsenettproxyClient,
    private val pdlClient: PdlClient,
) {
    suspend fun sendArenaDialogmeldingToMQ(
        receivedDialogmelding: ReceivedDialogmelding,
    ) {
        val loggingMeta = receivedDialogmelding.loggingMeta()
        val tssId = getTssId(
            receivedDialogmelding = receivedDialogmelding,
            loggingMeta = loggingMeta,
        )
        val pasientNavn = pdlClient.hentPersonNavn(
            personIdent = PersonIdent(receivedDialogmelding.personNrPasient),
        )
        val avsenderBehandler = syfohelsenettproxyClient.finnBehandler(
            behandlerFnr = receivedDialogmelding.personNrLege,
            msgId = receivedDialogmelding.msgId,
            loggingMeta = loggingMeta,
        )?.toBehandler(receivedDialogmelding.personNrLege)

        val arenaDialogNotat = createArenaDialogNotat(
            receivedDialogmelding = receivedDialogmelding,
            tssid = tssId,
            pasientNavn = pasientNavn,
            avsenderBehandler = avsenderBehandler,
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

    suspend fun isMeldingStoredInKelvin(msgId: String): Boolean {
        return aapApiClient.isMeldingInKelvin(msgId)
    }

    private suspend fun getTssId(
        receivedDialogmelding: ReceivedDialogmelding,
        loggingMeta: LoggingMeta,
    ): String {
        val tssId = smtssClient.findBestTss(
            legePersonIdent = PersonIdent(receivedDialogmelding.personNrLege),
            legekontorOrgName = receivedDialogmelding.legekontorOrgName,
            requestId = receivedDialogmelding.msgId,
        )

        if (tssId != null && tssId.tssid.isNotBlank()) {
            registerEmottakSubscription(
                tssId = tssId,
                receivedDialogmelding = receivedDialogmelding,
                loggingMeta = loggingMeta,
            )
        }

        return tssId?.tssid ?: ""
    }

    // eMottak-abonnement gjelder kun meldinger mottatt via MQ, og krever data fra fellesformatet.
    private suspend fun registerEmottakSubscription(
        tssId: TssId,
        receivedDialogmelding: ReceivedDialogmelding,
        loggingMeta: LoggingMeta,
    ) {
        val fellesformatXml = safeUnmarshal(receivedDialogmelding.fellesformat)
        val msgHead: XMLMsgHead = fellesformatXml.get()
        emottakService.registerEmottakSubscription(
            tssId = tssId,
            partnerReferanse = fellesformatXml.get<XMLMottakenhetBlokk>().partnerReferanse,
            sender = msgHead.msgInfo.sender,
            msgId = receivedDialogmelding.msgId,
            loggingMeta = loggingMeta,
        )
    }
}
