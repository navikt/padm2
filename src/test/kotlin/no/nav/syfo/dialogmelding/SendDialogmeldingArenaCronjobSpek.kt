package no.nav.syfo.dialogmelding

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.*
import no.nav.syfo.application.SendDialogmeldingArenaCronjob
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.TssId
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.isbehandlerdialog.BehandlerdialogClient
import no.nav.syfo.dropData
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.db.*
import no.nav.syfo.services.ArenaDialogmeldingService
import no.nav.syfo.services.EmottakService
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.util.safeUnmarshal
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

class SendDialogmeldingArenaCronjobSpek : Spek({
    describe(SendDialogmeldingArenaCronjob::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val mqSender = mockk<MQSenderInterface>(relaxed = true)
            val emottakService = mockk<EmottakService>(relaxed = true)
            val smtssClient = mockk<SmtssClient>(relaxed = true)
            val azureAdV2ClientMock = mockk<AzureAdV2Client>(relaxed = true)

            val arenaDialogmeldingService = ArenaDialogmeldingService(
                mqSender = mqSender,
                smtssClient = smtssClient,
                emottakService = emottakService,
                behandlerdialogClient = BehandlerdialogClient(
                    azureAdV2Client = azureAdV2ClientMock,
                    behandlerdialogClientId = externalMockEnvironment.environment.isbehandlerdialogClientId,
                    behandlerdialogUrl = externalMockEnvironment.environment.isbehandlerdialogUrl,
                )
            )
            val sendDialogmeldingArenaCronjob = SendDialogmeldingArenaCronjob(
                database = database,
                arenaDialogmeldingService = arenaDialogmeldingService,
            )

            describe("Send dialogmeldinger to Arena via cronjob") {
                beforeEachTest {
                    database.dropData()
                    clearAllMocks()
                    justRun { mqSender.sendArena(any()) }
                    coEvery { smtssClient.findBestTss(any(), any(), any()) } returns TssId("123")
                    coJustRun { emottakService.registerEmottakSubscription(any(), any(), any(), any(), any()) }
                }

                val fellesformat =
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
                val fellesformatXml = safeUnmarshal(fellesformat)
                val receivedDialogmelding = ReceivedDialogmelding.create(
                    dialogmeldingId = UUID.randomUUID().toString(),
                    fellesformat = fellesformatXml,
                    inputMessageText = fellesformat,
                )

                fun createDialogmeldingOpplysning(
                    receivedDialogmelding: ReceivedDialogmelding,
                    apprecStatus: Status = Status.OK
                ): String {
                    database.connection.use {
                        val id = it.opprettDialogmeldingOpplysninger(
                            receivedDialogmelding = receivedDialogmelding,
                        )
                        it.opprettBehandlingsutfall(
                            validationResult = ValidationResult(
                                status = apprecStatus,
                                ruleHits = emptyList(),
                            ),
                            dialogmeldingid = id,
                        )
                        it.commit()
                        return id
                    }
                }

                it("Sends dialogmelding to arena when melding is sent to kafka, and has sent a positive apprec which is older than 10min, and not stored in Modia") {
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmelding)
                    database.lagreSendtKafka(dialogmeldingId)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusMinutes(11)),
                    )

                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()

                        result.updated shouldBeEqualTo 1
                        result.failed shouldBeEqualTo 0
                    }
                    verify(exactly = 1) { mqSender.sendArena(any()) }
                    val arena = database.getSentToArena(dialogmeldingId)
                    arena?.first shouldNotBeEqualTo null
                    arena?.second shouldBeEqualTo true
                }

                it("Does not send when no apprec") {
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmelding)
                    database.lagreSendtKafka(dialogmeldingId)

                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()

                        result.updated shouldBeEqualTo 0
                        result.failed shouldBeEqualTo 0
                    }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                }

                it("Does not send when dialogmelding already sent to arena") {
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmelding)
                    database.lagreSendtKafka(dialogmeldingId)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusMinutes(11)),
                    )
                    database.lagreSendtArena(
                        dialogmeldingid = dialogmeldingId,
                        isSent = true,
                    )

                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()

                        result.updated shouldBeEqualTo 0
                        result.failed shouldBeEqualTo 0
                    }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                }

                it("Does not send when no new dialogmelding received") {
                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()

                        result.updated shouldBeEqualTo 0
                        result.failed shouldBeEqualTo 0
                    }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                }

                it("Does not send when apprec sent within 10 minutes") {
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmelding)
                    database.lagreSendtKafka(dialogmeldingId)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)),
                    )

                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()

                        result.updated shouldBeEqualTo 0
                        result.failed shouldBeEqualTo 0
                    }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                }

                it("Does not send when not published to kafka") {
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmelding)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusMinutes(11)),
                    )

                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()

                        result.updated shouldBeEqualTo 0
                        result.failed shouldBeEqualTo 0
                    }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                }

                it("Does not send if melding has apprec = INVALID") {
                    val dialogmeldingId = createDialogmeldingOpplysning(
                        receivedDialogmelding = receivedDialogmelding,
                        apprecStatus = Status.INVALID,
                    )
                    database.lagreSendtKafka(dialogmeldingId)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusMinutes(11)),
                    )

                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()

                        result.updated shouldBeEqualTo 0
                        result.failed shouldBeEqualTo 0
                    }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                }

                it("Does not send when melding lagret in modia") {
                    val fellesformatMeldingInBehandlerdialog =
                        getFileAsString("src/test/resources/dialogmelding_dialog_notat_in_behandlerdialog.xml")
                    val fellesformatXmlMeldingInBehandlerdialog = safeUnmarshal(fellesformatMeldingInBehandlerdialog)
                    val receivedDialogmeldingInBehandlerdialog = ReceivedDialogmelding.create(
                        dialogmeldingId = UUID.randomUUID().toString(),
                        fellesformat = fellesformatXmlMeldingInBehandlerdialog,
                        inputMessageText = fellesformatMeldingInBehandlerdialog,
                    )
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmeldingInBehandlerdialog)
                    database.lagreSendtKafka(dialogmeldingId)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusMinutes(11)),
                    )

                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()

                        result.updated shouldBeEqualTo 1
                        result.failed shouldBeEqualTo 0
                    }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                }
                it("Does not send when melding if is svar på dialogmøteinnkalling") {
                    val fellesformatMeldingSvarMoteinnkalling =
                        getFileAsString("src/test/resources/dialogmelding_dialog_svar_innkalling_dialogmote.xml")
                    val fellesformatXmlMeldingInBehandlerdialog = safeUnmarshal(fellesformatMeldingSvarMoteinnkalling)
                    val receivedDialogmeldingInBehandlerdialog = ReceivedDialogmelding.create(
                        dialogmeldingId = UUID.randomUUID().toString(),
                        fellesformat = fellesformatXmlMeldingInBehandlerdialog,
                        inputMessageText = fellesformatMeldingSvarMoteinnkalling,
                    )
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmeldingInBehandlerdialog)
                    database.lagreSendtKafka(dialogmeldingId)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusMinutes(11)),
                    )

                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()

                        result.updated shouldBeEqualTo 1
                        result.failed shouldBeEqualTo 0
                    }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                }

                it("Does not process dialogmelding when already checked whether it should be sent to arena") {
                    val fellesformatMeldingInBehandlerdialog =
                        getFileAsString("src/test/resources/dialogmelding_dialog_notat_in_behandlerdialog.xml")
                    val fellesformatXmlMeldingInBehandlerdialog = safeUnmarshal(fellesformatMeldingInBehandlerdialog)
                    val receivedDialogmeldingMeldingInBehandlerdialog = ReceivedDialogmelding.create(
                        dialogmeldingId = UUID.randomUUID().toString(),
                        fellesformat = fellesformatXmlMeldingInBehandlerdialog,
                        inputMessageText = fellesformatMeldingInBehandlerdialog,
                    )
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmeldingMeldingInBehandlerdialog)
                    database.lagreSendtKafka(dialogmeldingId)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusMinutes(11)),
                    )

                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()

                        result.updated shouldBeEqualTo 1
                        result.failed shouldBeEqualTo 0
                    }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                    val arena = database.getSentToArena(dialogmeldingId)
                    arena?.first shouldNotBeEqualTo null
                    arena?.second shouldBeEqualTo false

                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()

                        result.updated shouldBeEqualTo 0
                        result.failed shouldBeEqualTo 0
                    }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                }

                it("Fails when behandlerdialog gives InternalServerError") {
                    val fellesformatMeldingThrowsError =
                        getFileAsString("src/test/resources/dialogmelding_dialog_notat_in_behandlerdialog_with_error.xml")
                    val fellesformatXmlMeldingThrowsError = safeUnmarshal(fellesformatMeldingThrowsError)
                    val receivedDialogmeldingThrowsError = ReceivedDialogmelding.create(
                        dialogmeldingId = UUID.randomUUID().toString(),
                        fellesformat = fellesformatXmlMeldingThrowsError,
                        inputMessageText = fellesformatMeldingThrowsError,
                    )
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmeldingThrowsError)
                    database.lagreSendtKafka(dialogmeldingId)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusMinutes(11)),
                    )

                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()

                        result.updated shouldBeEqualTo 0
                        result.failed shouldBeEqualTo 1
                    }
                    verify(exactly = 0) { mqSender.sendArena(any()) }
                }

                it("Fails when sending on MQ throws error") {
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmelding)
                    database.lagreSendtKafka(dialogmeldingId)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusMinutes(11)),
                    )

                    every { mqSender.sendArena(any()) } throws Exception()

                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()

                        result.updated shouldBeEqualTo 0
                        result.failed shouldBeEqualTo 1
                    }
                    verify(exactly = 1) { mqSender.sendArena(any()) }

                    val arenaNotSent = database.getSentToArena(dialogmeldingId)
                    arenaNotSent?.first shouldBeEqualTo null
                    arenaNotSent?.second shouldBeEqualTo false
                }

                it("Does not fail the whole job when one erroneous melding, while other meldinger are OK") {
                    val fellesformatOtherPerson =
                        getFileAsString("src/test/resources/dialogmelding_dialog_svar_foresporsel_om_pasient.xml")
                    val fellesformatXmlOtherPerson = safeUnmarshal(fellesformatOtherPerson)
                    val receivedDialogmeldingWithError = ReceivedDialogmelding.create(
                        dialogmeldingId = UUID.randomUUID().toString(),
                        fellesformat = fellesformatXmlOtherPerson,
                        inputMessageText = fellesformatOtherPerson,
                    )
                    val dialogmeldingIdWithError = createDialogmeldingOpplysning(receivedDialogmeldingWithError)
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmelding)
                    database.lagreSendtKafka(dialogmeldingIdWithError)
                    database.lagreSendtApprec(dialogmeldingIdWithError)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingIdWithError,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusMinutes(11)),
                    )
                    database.lagreSendtKafka(dialogmeldingId)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusMinutes(11)),
                    )

                    every {
                        mqSender.sendArena(
                            match<String> { arenaDialogNotatStringifyed ->
                                arenaDialogNotatStringifyed.contains(UserConstants.PATIENT_FNR_FORESPORSEL_SVAR)
                            }
                        )
                    } throws Exception()

                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()

                        result.updated shouldBeEqualTo 1
                        result.failed shouldBeEqualTo 1
                    }
                    verify(exactly = 2) { mqSender.sendArena(any()) }

                    val arena = database.getSentToArena(dialogmeldingId)
                    arena?.first shouldNotBeEqualTo null
                    arena?.second shouldBeEqualTo true

                    val arenaNotSent = database.getSentToArena(dialogmeldingIdWithError)
                    arenaNotSent?.first shouldBeEqualTo null
                    arenaNotSent?.second shouldBeEqualTo false
                }
            }
        }
    }
})
