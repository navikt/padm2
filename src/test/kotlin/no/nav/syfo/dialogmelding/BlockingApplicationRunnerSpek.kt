package no.nav.syfo.dialogmelding

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.syfo.*
import no.nav.syfo.application.BlockingApplicationRunner
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.util.getFileAsString
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import javax.jms.*

class BlockingApplicationRunnerSpek : Spek({

    describe(BlockingApplicationRunnerSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val session = mockk<Session>()
            val receiptProducer = mockk<MessageProducer>(relaxed = true)
            val backoutProducer = mockk<MessageProducer>(relaxed = true)
            val arenaProducer = mockk<MessageProducer>(relaxed = true)
            val dialogmeldingProducer = mockk<DialogmeldingProducer>(relaxed = true)
            val subscriptionEmottak = mockk<SubscriptionPort>(relaxed = true)

            val blockingApplicationRunner = BlockingApplicationRunner(
                applicationState = externalMockEnvironment.applicationState,
                database = database,
                env = externalMockEnvironment.environment,
                inputconsumer = mockk(),
                session = session,
                receiptProducer = receiptProducer,
                backoutProducer = backoutProducer,
                arenaProducer = arenaProducer,
                dialogmeldingProducer = dialogmeldingProducer,
                subscriptionEmottak = subscriptionEmottak,
            )
            val incomingMessage = mockk<TextMessage>(relaxed = true)

            describe("Prosesserer innkommet melding") {

                beforeEachTest {
                    database.dropData()
                    clearAllMocks()
                    every { session.createTextMessage() } returns(mockk(relaxed = true))
                    justRun { receiptProducer.send(any()) }
                    justRun { backoutProducer.send(any()) }
                    justRun { arenaProducer.send(any()) }
                    justRun { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                    justRun { subscriptionEmottak.startSubscription(any()) }
                }
                it("Prosesserer innkommet melding") {
                    val fellesformat =
                        getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 1) { arenaProducer.send(any()) }
                    verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (ugyldig innbyggerid)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010142365", "01010142366")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 0) { arenaProducer.send(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (pdfgen feiler)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010142365", UserConstants.PATIENT_FNR_PDFGEN_FAIL)
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 0) { receiptProducer.send(any()) }
                    verify(exactly = 1) { backoutProducer.send(any()) }
                    verify(exactly = 0) { arenaProducer.send(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                }
            }
        }
    }
})
