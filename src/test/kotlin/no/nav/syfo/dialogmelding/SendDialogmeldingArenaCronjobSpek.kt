package no.nav.syfo.dialogmelding

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.application.SendDialogmeldingArenaCronjob
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.TssId
import no.nav.syfo.dropData
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.persistering.db.*
import no.nav.syfo.services.ArenaDialogmeldingService
import no.nav.syfo.services.EmottakService
import no.nav.syfo.updateSendtApprec
import no.nav.syfo.util.getFellesformatXMLFromString
import no.nav.syfo.util.getFileAsString
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

class SendDialogmeldingArenaCronjobSpek: Spek ({
    describe(SendDialogmeldingArenaCronjob::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val mqSender = mockk<MQSenderInterface>(relaxed = true)
            val emottakService = mockk<EmottakService>(relaxed = true)
            val smtssClient = mockk<SmtssClient>(relaxed = true)
            
            val arenaDialogmeldingService = ArenaDialogmeldingService(
                mqSender = mqSender,
                smtssClient = smtssClient,
                emottakService = emottakService,
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
                val fellesformatXml = getFellesformatXMLFromString(fellesformat)
                val receivedDialogmelding = ReceivedDialogmelding.create(
                    dialogmeldingId = UUID.randomUUID().toString(),
                    fellesformat = fellesformatXml,
                    inputMessageText = fellesformat,
                )
                
                fun createDialogmeldingOpplysning(receivedDialogmelding: ReceivedDialogmelding): String {
                    database.connection.use {
                        val id = it.opprettDialogmeldingOpplysninger(
                            receivedDialogmelding = receivedDialogmelding,
                        )
                        it.commit()
                        return id
                    }
                }
                
                it("Sends dialogmelding to arena") {
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmelding)
                    database.lagreSendtKafka(dialogmeldingId)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusHours(3)),
                    )
                    
                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()
                        
                        result.updated shouldBeEqualTo 1
                        result.failed shouldBeEqualTo 0
                    }
                }
                
                it("Does not send when no apprec") {
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmelding)
                    database.lagreSendtKafka(dialogmeldingId)
                    
                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()
                        
                        result.updated shouldBeEqualTo 0
                        result.failed shouldBeEqualTo 0
                    }
                }
                
                it("Does not send when dialogmelding already sent to arena") {
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmelding)
                    database.lagreSendtKafka(dialogmeldingId)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusHours(3)),
                    )
                    database.lagreSendtArena(dialogmeldingId)
                    
                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()
                        
                        result.updated shouldBeEqualTo 0
                        result.failed shouldBeEqualTo 0
                    }
                }
                
                it("Does not send when no new dialogmelding received") {
                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()
                        
                        result.updated shouldBeEqualTo 0
                        result.failed shouldBeEqualTo 0
                    }
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
                }
                
                it("Does not send when not published to kafka") {
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmelding)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusHours(3)),
                    )
                    
                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()
                        
                        result.updated shouldBeEqualTo 0
                        result.failed shouldBeEqualTo 0
                    }
                }
                
                it("Fails when sending on MQ throws error") {
                    val dialogmeldingId = createDialogmeldingOpplysning(receivedDialogmelding)
                    database.lagreSendtKafka(dialogmeldingId)
                    database.lagreSendtApprec(dialogmeldingId)
                    database.updateSendtApprec(
                        dialogmeldingId = dialogmeldingId,
                        timestamp = Timestamp.valueOf(LocalDateTime.now().minusHours(3)),
                    )
                    
                    every { mqSender.sendArena(any()) } throws Exception()
                    
                    runBlocking {
                        val result = sendDialogmeldingArenaCronjob.runJob()
                        
                        result.updated shouldBeEqualTo 0
                        result.failed shouldBeEqualTo 1
                    }
                }
            }
        }
    }
})