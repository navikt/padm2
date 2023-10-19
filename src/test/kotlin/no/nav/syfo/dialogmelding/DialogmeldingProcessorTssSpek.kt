package no.nav.syfo.dialogmelding

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.application.DialogmeldingProcessor
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.TssId
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.handlestatus.handleStatusOK
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.persistering.persistRecivedMessageValidation
import no.nav.syfo.services.EmottakService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.get
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.util.safeUnmarshal
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class DialogmeldingProcessorTssSpek : Spek({

    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database
    val dialogmeldingProducer = mockk<DialogmeldingProducer>(relaxed = true)
    val mqSender = mockk<MQSenderInterface>(relaxed = true)
    val azureAdV2Client = mockk<AzureAdV2Client>(relaxed = true)
    val emottakService = mockk<EmottakService>(relaxed = true)
    val smtssClient = mockk<SmtssClient>(relaxed = true)

    beforeEachTest {
        mockkStatic("no.nav.syfo.handlestatus.HandleStatusOKKt")
        mockkStatic("no.nav.syfo.persistering.HandleRecivedMessageKt")
        coJustRun {
            handleStatusOK(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any()
            )
        }
        justRun { persistRecivedMessageValidation(any(), any(), any()) }
    }

    afterEachTest {
        unmockkStatic("no.nav.syfo.handlestatus.HandleStatusOKKt")
        unmockkStatic("no.nav.syfo.persistering.HandleRecivedMessageKt")
        clearMocks(dialogmeldingProducer, mqSender, azureAdV2Client, emottakService, smtssClient)
    }

    val dialogmeldingProcessor = DialogmeldingProcessor(
        database = database,
        env = externalMockEnvironment.environment,
        mqSender = mqSender,
        dialogmeldingProducer = dialogmeldingProducer,
        azureAdV2Client = azureAdV2Client,
        smtssClient = smtssClient,
        emottakService = emottakService,
    )

    describe("Get and handle TSS ident") {
        val tssId = TssId("123")
        val msgId = "37340D30-FE14-42B5-985F-A8FF8FFA0CB5"
        val LEGEKONTOR_ORGNAME = "Kule helsetjenester AS"

        val dialogmeldingString = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
        val fellesformat = safeUnmarshal(dialogmeldingString)

        val emottakBlokk = fellesformat.get<XMLMottakenhetBlokk>()
        val personNumberDoctor = emottakBlokk.avsenderFnrFraDigSignatur
        val legePersonIdent = PersonIdent(personNumberDoctor)

        it("Update emottak subscription if TSS") {
            val partnerReferanse = "13123"
            val loggingMeta = LoggingMeta("1901162157lege21826.1", "223456789", msgId, "")
            coEvery { smtssClient.findBestTss(any(), any(), any()) } returns tssId
            coJustRun { emottakService.registerEmottakSubscription(any(), any(), any(), any(), any()) }

            runBlocking {
                dialogmeldingProcessor.process(msgId, dialogmeldingString)
            }

            coVerify(exactly = 1) { smtssClient.findBestTss(legePersonIdent, LEGEKONTOR_ORGNAME, msgId) }
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
                dialogmeldingProcessor.process(msgId, dialogmeldingString)
            }

            coVerify(exactly = 1) { smtssClient.findBestTss(legePersonIdent, LEGEKONTOR_ORGNAME, msgId) }
            coVerify(exactly = 0) { emottakService.registerEmottakSubscription(any(), any(), any(), any(), any()) }
        }

        it("Don't update emottak subscription if empty string in TssId") {
            val emptyTssId = TssId("")
            coEvery { smtssClient.findBestTss(any(), any(), any()) } returns emptyTssId

            runBlocking {
                dialogmeldingProcessor.process(msgId, dialogmeldingString)
            }

            coVerify(exactly = 1) { smtssClient.findBestTss(legePersonIdent, LEGEKONTOR_ORGNAME, msgId) }
            coVerify(exactly = 0) { emottakService.registerEmottakSubscription(any(), any(), any(), any(), any()) }
        }
    }
})
