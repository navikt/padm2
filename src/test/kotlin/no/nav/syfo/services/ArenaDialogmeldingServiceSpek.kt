package no.nav.syfo.services

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.TssId
import no.nav.syfo.client.isbehandlerdialog.BehandlerdialogClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.get
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.util.safeUnmarshal
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class ArenaDialogmeldingServiceSpek : Spek({

    val emottakService = mockk<EmottakService>(relaxed = true)
    val smtssClient = mockk<SmtssClient>(relaxed = true)
    val mqSender = mockk<MQSenderInterface>(relaxed = true)
    val behandlerdialogClient = mockk<BehandlerdialogClient>(relaxed = true)

    val arenaDialogmeldingService = ArenaDialogmeldingService(
        mqSender = mqSender,
        smtssClient = smtssClient,
        emottakService = emottakService,
        behandlerdialogClient = behandlerdialogClient
    )

    afterEachTest {
        clearMocks(mqSender, emottakService, smtssClient)
    }

    describe("sendArenaDialogmeldingToMQ()") {
        val tssId = TssId("123")
        val msgId = "37340D30-FE14-42B5-985F-A8FF8FFA0CB5"
        val legekontorOrgName = "Kule helsetjenester AS"

        val dialogmeldingString = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
        val fellesformat = safeUnmarshal(dialogmeldingString)
        val receivedDialogmelding = ReceivedDialogmelding.create(
            dialogmeldingId = msgId,
            fellesformat = fellesformat,
            inputMessageText = dialogmeldingString,
        )

        val emottakBlokk = fellesformat.get<XMLMottakenhetBlokk>()
        val personNumberDoctor = emottakBlokk.avsenderFnrFraDigSignatur
        val legePersonIdent = PersonIdent(personNumberDoctor)

        it("Update emottak subscription if TSS") {
            val partnerReferanse = "13123"
            val loggingMeta = LoggingMeta("1901162157lege21826.1", "223456789", msgId, "")
            coEvery { smtssClient.findBestTss(any(), any(), any()) } returns tssId
            coJustRun { emottakService.registerEmottakSubscription(any(), any(), any(), any(), any()) }

            runBlocking {
                arenaDialogmeldingService.sendArenaDialogmeldingToMQ(receivedDialogmelding, fellesformat)
            }

            coVerify(exactly = 1) { smtssClient.findBestTss(legePersonIdent, legekontorOrgName, msgId) }
            coVerify(exactly = 1) {
                emottakService.registerEmottakSubscription(
                    tssId,
                    partnerReferanse,
                    any(),
                    msgId,
                    loggingMeta
                )
            }
        }

        it("Don't update emottak subscription if no tss") {
            coEvery { smtssClient.findBestTss(any(), any(), any()) } returns null

            runBlocking {
                arenaDialogmeldingService.sendArenaDialogmeldingToMQ(receivedDialogmelding, fellesformat)
            }

            coVerify(exactly = 1) { smtssClient.findBestTss(legePersonIdent, legekontorOrgName, msgId) }
            coVerify(exactly = 0) { emottakService.registerEmottakSubscription(any(), any(), any(), any(), any()) }
        }

        it("Don't update emottak subscription if empty string in TssId") {
            val emptyTssId = TssId("")
            coEvery { smtssClient.findBestTss(any(), any(), any()) } returns emptyTssId

            runBlocking {
                arenaDialogmeldingService.sendArenaDialogmeldingToMQ(receivedDialogmelding, fellesformat)
            }

            coVerify(exactly = 1) { smtssClient.findBestTss(legePersonIdent, legekontorOrgName, msgId) }
            coVerify(exactly = 0) { emottakService.registerEmottakSubscription(any(), any(), any(), any(), any()) }
        }
    }
})
