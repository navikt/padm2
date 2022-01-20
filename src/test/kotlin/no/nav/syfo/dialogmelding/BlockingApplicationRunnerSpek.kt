package no.nav.syfo.dialogmelding

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.syfo.*
import no.nav.syfo.application.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.metrics.MESSAGES_STILL_FAIL_AFTER_1H
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.util.getFileAsStringISO88591
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import javax.jms.*

class BlockingApplicationRunnerSpek : Spek({

    describe(BlockingApplicationRunnerSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val mqSender = mockk<MQSenderInterface>(relaxed = true)
            val dialogmeldingProducer = mockk<DialogmeldingProducer>(relaxed = true)
            val subscriptionEmottak = mockk<SubscriptionPort>(relaxed = true)
            val incomingMessage = mockk<TextMessage>(relaxed = true)

            val blockingApplicationRunner = BlockingApplicationRunner(
                applicationState = externalMockEnvironment.applicationState,
                database = database,
                env = externalMockEnvironment.environment,
                inputconsumer = mockk(),
                mqSender = mqSender,
                dialogmeldingProducer = dialogmeldingProducer,
                subscriptionEmottak = subscriptionEmottak,
            )
            val dialogmeldingProcessor = DialogmeldingProcessor(
                database = database,
                env = externalMockEnvironment.environment,
                mqSender = mqSender,
                dialogmeldingProducer = dialogmeldingProducer,
                subscriptionEmottak = subscriptionEmottak,
            )
            val rerunCronJob = RerunCronJob(
                database = database,
                dialogmeldingProcessor = dialogmeldingProcessor,
            )

            describe("Prosesserer innkommet melding") {

                beforeEachTest {
                    database.dropData()
                    clearAllMocks()
                    justRun { mqSender.sendArena(any()) }
                    justRun { mqSender.sendReceipt(any()) }
                    justRun { mqSender.sendBackout(any()) }
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
                    verify(exactly = 1) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 1) { mqSender.sendArena(any()) }
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
                    verify(exactly = 0) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }

                    externalMockEnvironment.pdfgenMock.alwaysFail = false
                    runBlocking {
                        rerunCronJob.run()
                    }
                    verify(exactly = 1) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 1) { mqSender.sendArena(any()) }
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
                    verify(exactly = 1) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (duplikat)") {
                    val fellesformat =
                        getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 1) { mqSender.sendArena(any()) }
                    verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 2) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 1) { mqSender.sendArena(any()) }
                    verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (duplikat, med vedlegg)") {
                    val fellesformat =
                        getFileAsString("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 1) { mqSender.sendArena(any()) }
                    verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 2) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 1) { mqSender.sendArena(any()) }
                    verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (ikke duplikat, bare nesten)") {
                    val fellesformat =
                        getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 1) { mqSender.sendArena(any()) }
                    verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                    val fellesformatNestenDuplikat =
                        getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
                            .replace("Et vedlegg fra lege", "Et vedlegg fra lege nesten likt")
                    every { incomingMessage.text } returns(fellesformatNestenDuplikat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 2) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 2) { mqSender.sendArena(any()) }
                    verify(exactly = 2) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (manglende innbyggerid)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010142365", "")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (ugyldig innbyggerid)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010142365", "01010142366")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (ugyldig innbyggerid først, og så gyldig melding etterpå skal ikke gi duplikat)") {
                    val fellesformatUgyldig = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010142365", "01010142366")
                    every { incomingMessage.text } returns(fellesformatUgyldig)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }

                    val fellesformatGyldigMenSammeShaString = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    every { incomingMessage.text } returns(fellesformatGyldigMenSammeShaString)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 2) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 1) { mqSender.sendArena(any()) }
                    verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }

                it("Prosesserer innkommet melding (pdfgen feiler, gammel mottattdato)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010142365", UserConstants.PATIENT_FNR_PDFGEN_FAIL)
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 0) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                    MESSAGES_STILL_FAIL_AFTER_1H.get() shouldBeEqualTo 0.0
                    externalMockEnvironment.pdfgenMock.allowFail = false
                    runBlocking {
                        rerunCronJob.run()
                    }
                    externalMockEnvironment.pdfgenMock.allowFail = true
                    verify(exactly = 1) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                    MESSAGES_STILL_FAIL_AFTER_1H.get() shouldBeEqualTo 0.0
                }
                it("Prosesserer innkommet melding (pdfgen feiler, gammel mottattdato, feiler også ved rerun)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010142365", UserConstants.PATIENT_FNR_PDFGEN_FAIL)
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 0) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                    MESSAGES_STILL_FAIL_AFTER_1H.get() shouldBeEqualTo 0.0
                    runBlocking {
                        rerunCronJob.run()
                    }
                    verify(exactly = 0) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                    MESSAGES_STILL_FAIL_AFTER_1H.get() shouldBeEqualTo 1.0
                }
                it("Prosesserer innkommet melding (pdfgen feiler, mottattdato nå)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010142365", UserConstants.PATIENT_FNR_PDFGEN_FAIL)
                        .replace("mottattDatotid=\"2019-01-16T21:57:43\"", "mottattDatotid=\"${java.time.Instant.now()}\"")
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 0) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                    externalMockEnvironment.pdfgenMock.allowFail = false
                    runBlocking {
                        // Meldingen må være ti minutter gammel for å bli plukket opp av cronjobben
                        rerunCronJob.run()
                    }
                    externalMockEnvironment.pdfgenMock.allowFail = true
                    verify(exactly = 0) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (ingen aktoer id)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010142365", UserConstants.PATIENT_FNR_NO_AKTOER_ID)
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (lege ugyldig fnr)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010112377", UserConstants.BEHANDLER_FNR_UGYLDIG)
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
                it("Prosesserer innkommet melding (lege ikke autorisert)") {
                    val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("01010112377", UserConstants.BEHANDLER_FNR_IKKE_AUTORISERT)
                    every { incomingMessage.text } returns(fellesformat)
                    runBlocking {
                        blockingApplicationRunner.processMessageHandleException(incomingMessage)
                    }
                    verify(exactly = 1) { mqSender.sendReceipt(any()) }
                    verify(exactly = 0) { mqSender.sendBackout(any()) }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any(), any(), any()) }
                }
            }
        }
    }
})
