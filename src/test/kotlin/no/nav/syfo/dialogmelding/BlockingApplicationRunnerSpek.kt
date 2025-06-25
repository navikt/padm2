package no.nav.syfo.dialogmelding

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.*
import no.nav.syfo.application.*
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.TssId
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.metrics.MESSAGES_STILL_FAIL_AFTER_1H
import no.nav.syfo.persistering.db.hentDialogmeldingOpplysningerJournalpostId
import no.nav.syfo.services.EmottakService
import no.nav.syfo.util.*
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.sql.Timestamp
import java.time.LocalDateTime
import javax.jms.*

class BlockingApplicationRunnerSpek : Spek({

    describe(BlockingApplicationRunnerSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val mqSender = mockk<MQSenderInterface>(relaxed = true)
        val dialogmeldingProducer = mockk<DialogmeldingProducer>(relaxed = true)
        val incomingMessage = mockk<TextMessage>(relaxed = true)
        val azureAdV2Client = mockk<AzureAdV2Client>(relaxed = true)
        val emottakService = mockk<EmottakService>(relaxed = true)
        val smtssClient = mockk<SmtssClient>(relaxed = true)

        val dialogmeldingProcessor = DialogmeldingProcessor(
            database = database,
            env = externalMockEnvironment.environment,
            mqSender = mqSender,
            dialogmeldingProducer = dialogmeldingProducer,
            azureAdV2Client = azureAdV2Client,
        )
        val blockingApplicationRunner = BlockingApplicationRunner(
            applicationState = externalMockEnvironment.applicationState,
            database = database,
            inputconsumer = mockk(),
            mqSender = mqSender,
            dialogmeldingProcessor = dialogmeldingProcessor,
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
                justRun { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                coEvery { smtssClient.findBestTss(any(), any(), any()) } returns TssId("123")
                coJustRun { emottakService.registerEmottakSubscription(any(), any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (melding ok)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                every { incomingMessage.text } returns(fellesformat)
                val dialogmeldingId = runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                dialogmeldingId shouldNotBe null
                val journalpostId = database.hentDialogmeldingOpplysningerJournalpostId(dialogmeldingId!!)
                journalpostId shouldNotBe null
            }
            it("Prosesserer innkommet melding (melding mangler navn)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat_missing_givenname.xml")
                every { incomingMessage.text } returns(fellesformat)
                val dialogmeldingId = runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                dialogmeldingId shouldNotBe null
                val journalpostId = database.hentDialogmeldingOpplysningerJournalpostId(dialogmeldingId!!)
                journalpostId shouldNotBe null
            }
            it("Prosesserer innkommet melding (ukjent behandler)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat_ukjent_behandler.xml")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet dialogmotesvar (ok)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_svar_innkalling_dialogmote.xml")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet dialogmotesvar (ugyldig kodeverk)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_svar_innkalling_dialogmote_invalid.xml")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet test-melding fra syfomock (melding ok)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("<MsgId>37340D30-FE14-42B5-985F-A8FF8FFA0CB5</MsgId>", "<MsgId>syfomock-37340D30-FE14-42B5-985F-A8FF8FFA0CB5</MsgId>")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 0) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (melding ok, men pdf-gen feiler)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                every { incomingMessage.text } returns(fellesformat)
                externalMockEnvironment.pdfgenMock.alwaysFail = true
                val dialogmeldingId = runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 0) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }

                externalMockEnvironment.pdfgenMock.alwaysFail = false
                database.updateCreatedAt(
                    dialogmeldingId!!,
                    Timestamp.valueOf(LocalDateTime.now().minusMinutes(20L))
                )
                runBlocking {
                    rerunCronJob.run()
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }

            it("Prosesserer innkommet melding (tekst i notatinnhold mangler)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("<TekstNotatInnhold xsi:type=\"xsd:string\">Hei,Det gjelder pas. Sender som vedlegg epikrisen</TekstNotatInnhold>", "")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (behandler fornavn og mellomnavn mangler)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                        .replace("<MiddleName>Fos</MiddleName>", "")
                        .replace("<GivenName>Inga</GivenName>", "")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (ugyldig kodeverk)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_svar_foresporsel_om_pasient_invalid.xml")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (ugyldig hpr-nr)") {
                val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    .replace("<Id>1234567</Id>", "<Id>-1234567</Id>")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (vedlegg som feiler virussjekk)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
                        .replace("Et vedlegg fra lege", "problem file")
                every { incomingMessage.text } returns(fellesformat)
                val dialogmeldingId = runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                dialogmeldingId shouldNotBe null
                val journalpostId = database.hentDialogmeldingOpplysningerJournalpostId(dialogmeldingId!!)
                journalpostId shouldBe null
            }
            it("Prosesserer innkommet melding (vedlegg ok)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
                every { incomingMessage.text } returns(fellesformat)
                val dialogmeldingId = runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                val antallVedleggSlot = slot<Int>()
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), capture(antallVedleggSlot)) }
                dialogmeldingId shouldNotBe null
                val journalpostId = database.hentDialogmeldingOpplysningerJournalpostId(dialogmeldingId!!)
                journalpostId shouldNotBe null
                antallVedleggSlot.captured shouldBeEqualTo 2
            }
            it("Prosesserer innkommet melding (vedlegg med mismatch type)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
                        .replace("<MimeType>image/jpeg</MimeType>", "<MimeType>application/pdf</MimeType>")
                every { incomingMessage.text } returns(fellesformat)
                val dialogmeldingId = runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                val antallVedleggSlot = slot<Int>()
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), capture(antallVedleggSlot)) }
                dialogmeldingId shouldNotBe null
                val journalpostId = database.hentDialogmeldingOpplysningerJournalpostId(dialogmeldingId!!)
                journalpostId shouldNotBe null
                antallVedleggSlot.captured shouldBeEqualTo 1
            }
            it("Prosesserer innkommet melding (ikke duplikat siden pasient forskjellig)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                every { incomingMessage.text } returns(
                    fellesformat.replace("<Id>01010142365</Id>", "<Id>45088649080</Id>")
                    )
                val dialogmeldingId = runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 2) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 2) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                dialogmeldingId shouldNotBe null
                val journalpostId = database.hentDialogmeldingOpplysningerJournalpostId(dialogmeldingId!!)
                journalpostId shouldNotBe null
            }
            it("Prosesserer innkommet melding (duplikat)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                val dialogmeldingId = runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 2) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                dialogmeldingId shouldNotBe null
                val journalpostId = database.hentDialogmeldingOpplysningerJournalpostId(dialogmeldingId!!)
                journalpostId shouldBe null
            }
            it("Prosesserer innkommet melding (duplikat, med vedlegg)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 2) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (ikke duplikat, mange vedlegg der det er en differanse i siste vedlegg)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat_mange_vedlegg.xml")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                every { incomingMessage.text } returns(fellesformat.replace("Et siste bilde fra lege", "Et aller siste bilde fra lege"))
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 2) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 2) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (ikke duplikat, men med så mange vedlegg at det blir duplikat likevel)") {
                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat_veldig_mange_vedlegg.xml")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                every { incomingMessage.text } returns(fellesformat.replace("Et siste bilde fra lege", "Et aller siste bilde fra lege"))
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 2) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (ikke duplikat, bare nesten)") {
                val fellesformat =
                    getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                val fellesformatNestenDuplikat =
                    getFileAsStringISO88591("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
                        .replace("Et vedlegg fra lege", "Et vedlegg fra lege nesten likt")
                every { incomingMessage.text } returns(fellesformatNestenDuplikat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 2) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 2) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (manglende innbyggerid)") {
                val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    .replace("01010142365", "")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (feil antall siffer i innbyggerid)") {
                val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    .replace("01010142365", "0101014236")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (ugyldig 11-sifret innbyggerid)") {
                val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    .replace("01010142365", "01010142366")
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (ugyldig innbyggerid først, og så gyldig melding etterpå skal ikke gi duplikat)") {
                val fellesformatUgyldig = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    .replace("01010142365", "01010142366")
                every { incomingMessage.text } returns(fellesformatUgyldig)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }

                val fellesformatGyldigMenSammeShaString = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                every { incomingMessage.text } returns(fellesformatGyldigMenSammeShaString)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 2) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }

            it("Prosesserer innkommet melding (pdfgen feiler, gammel created-at)") {
                val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    .replace("01010142365", UserConstants.PATIENT_FNR_PDFGEN_FAIL)
                every { incomingMessage.text } returns(fellesformat)
                val dialogmeldingId = runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 0) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                MESSAGES_STILL_FAIL_AFTER_1H.count() shouldBeEqualTo 0.0
                externalMockEnvironment.pdfgenMock.allowFail = false
                database.updateCreatedAt(
                    dialogmeldingId!!,
                    Timestamp.valueOf(LocalDateTime.now().minusMinutes(20L))
                )
                runBlocking {
                    rerunCronJob.run()
                }
                externalMockEnvironment.pdfgenMock.allowFail = true
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                MESSAGES_STILL_FAIL_AFTER_1H.count() shouldBeEqualTo 0.0
            }
            it("Prosesserer innkommet melding (pdfgen feiler, gammel created-at, feiler også ved rerun)") {
                val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    .replace("01010142365", UserConstants.PATIENT_FNR_PDFGEN_FAIL)
                every { incomingMessage.text } returns(fellesformat)
                val dialogmeldingId = runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 0) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                MESSAGES_STILL_FAIL_AFTER_1H.count() shouldBeEqualTo 0.0
                database.updateCreatedAt(
                    dialogmeldingId!!,
                    Timestamp.valueOf(LocalDateTime.now().minusMinutes(90L))
                )
                runBlocking {
                    rerunCronJob.run()
                }
                verify(exactly = 0) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                MESSAGES_STILL_FAIL_AFTER_1H.count() shouldBeEqualTo 1.0
            }
            it("Prosesserer innkommet melding (pdfgen feiler, created-at nå)") {
                val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    .replace("01010142365", UserConstants.PATIENT_FNR_PDFGEN_FAIL)
                    .replace("mottattDatotid=\"2019-01-16T21:57:43\"", "mottattDatotid=\"${java.time.Instant.now()}\"")
                every { incomingMessage.text } returns(fellesformat)
                val dialogmeldingId = runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 0) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
                externalMockEnvironment.pdfgenMock.allowFail = false
                database.updateCreatedAt(
                    dialogmeldingId!!,
                    Timestamp.valueOf(LocalDateTime.now().minusMinutes(8L))
                )
                runBlocking {
                    // Meldingen må være ti minutter gammel for å bli plukket opp av cronjobben
                    rerunCronJob.run()
                }
                externalMockEnvironment.pdfgenMock.allowFail = true
                verify(exactly = 0) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (allerede journalfort)") {
                val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    .replace("01010142365", UserConstants.PATIENT_FNR_JP_CONFLICT)
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 1) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (lege ugyldig fnr)") {
                val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    .replace("01010112377", UserConstants.BEHANDLER_FNR_UGYLDIG)
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
            it("Prosesserer innkommet melding (lege ikke autorisert)") {
                val fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                    .replace("01010112377", UserConstants.BEHANDLER_FNR_IKKE_AUTORISERT)
                every { incomingMessage.text } returns(fellesformat)
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                verify(exactly = 1) { mqSender.sendReceipt(any()) }
                verify(exactly = 0) { mqSender.sendBackout(any()) }
                verify(exactly = 0) { mqSender.sendArena(any()) }
                verify(exactly = 0) { dialogmeldingProducer.sendDialogmelding(any(), any(), any(), any()) }
            }
        }
    }
})
