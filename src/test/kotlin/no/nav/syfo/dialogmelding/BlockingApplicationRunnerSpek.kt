package no.nav.syfo.dialogmelding

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.syfo.*
import no.nav.syfo.application.BlockingApplicationRunner
import no.nav.syfo.application.RerunCronJob
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.util.getFileAsStringISO88591
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
            val rerunCronJob = RerunCronJob(
                database = database,
                blockingApplicationRunner = blockingApplicationRunner,
            )

            describe("Prosesserer innkommet melding") {

                beforeEachTest {
                    database.dropData()
                    clearAllMocks()
                    every { session.createTextMessage() } returns(mockk(relaxed = true))
                    justRun { receiptProducer.send(any()) }
                    justRun { backoutProducer.send(any()) }
                    justRun { arenaProducer.send(any()) }
                    justRun { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                    justRun { subscriptionEmottak.startSubscription(any()) }
                }
                it("Prosesserer innkommet melding (melding ok)") {
                    val fellesformat =
                        getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 1) { arenaProducer.send(any()) }
                    verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (melding ok, men pdf-gen feiler)") {
                    val fellesformat =
                        getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    every { incomingMessage.text } returns(fellesformat)
                    externalMockEnvironment.pdfgenMock.alwaysFail = true
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    externalMockEnvironment.pdfgenMock.alwaysFail = false
                    verify(exactly = 0) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 0) { arenaProducer.send(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }

                    runBlocking {
                        rerunCronJob.run()
                    }
                    externalMockEnvironment.pdfgenMock.alwaysFail = false
                    verify(exactly = 1) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 1) { arenaProducer.send(any()) }
                    verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }

                it("Prosesserer innkommet melding (tekst i notatinnhold mangler)") {
                    val fellesformat =
                        getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                            .replace("<TekstNotatInnhold xsi:type=\"xsd:string\">Hei,Det gjelder pas. Sender som vedlegg epikrisen</TekstNotatInnhold>", "")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 0) { arenaProducer.send(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (duplikat)") {
                    val fellesformat =
                        getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 1) { arenaProducer.send(any()) }
                    verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 2) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 1) { arenaProducer.send(any()) }
                    verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (duplikat, med vedlegg)") {
                    val fellesformat =
                        getFileAsString("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 1) { arenaProducer.send(any()) }
                    verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 2) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 1) { arenaProducer.send(any()) }
                    verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (ikke duplikat, bare nesten)") {
                    val fellesformat =
                        getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 1) { arenaProducer.send(any()) }
                    verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                    val fellesformatNestenDuplikat =
                        getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
                            .replace("Et vedlegg fra lege", "Et vedlegg fra lege nesten likt")
                    every { incomingMessage.text } returns(fellesformatNestenDuplikat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 2) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 2) { arenaProducer.send(any()) }
                    verify(exactly = 2) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (manglende innbyggerid)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010142365", "")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 0) { arenaProducer.send(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
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
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (pdfgen feiler, gammel mottattdato)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010142365", UserConstants.PATIENT_FNR_PDFGEN_FAIL)
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 0) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 0) { arenaProducer.send(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                    externalMockEnvironment.pdfgenMock.allowFail = false
                    runBlocking {
                        rerunCronJob.run()
                    }
                    externalMockEnvironment.pdfgenMock.allowFail = true
                    verify(exactly = 1) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 0) { arenaProducer.send(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (pdfgen feiler, mottattdato nå)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010142365", UserConstants.PATIENT_FNR_PDFGEN_FAIL)
                        .replace("mottattDatotid=\"2019-01-16T21:57:43\"", "mottattDatotid=\"${java.time.Instant.now()}\"")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 0) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 0) { arenaProducer.send(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                    externalMockEnvironment.pdfgenMock.allowFail = false
                    runBlocking {
                        // Meldingen må være to minutter gammel for å bli plukket opp av cronjobben
                        rerunCronJob.run()
                    }
                    externalMockEnvironment.pdfgenMock.allowFail = true
                    verify(exactly = 0) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 0) { arenaProducer.send(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (ingen aktoer id)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010142365", UserConstants.PATIENT_FNR_NO_AKTOER_ID)
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 0) { arenaProducer.send(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (lege ugyldig fnr)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010112377", UserConstants.BEHANDLER_FNR_UGYLDIG)
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 0) { arenaProducer.send(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (lege ikke autorisert)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010112377", UserConstants.BEHANDLER_FNR_IKKE_AUTORISERT)
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { receiptProducer.send(any()) }
                    verify(exactly = 0) { backoutProducer.send(any()) }
                    verify(exactly = 0) { arenaProducer.send(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
            }
        }
    }
})
