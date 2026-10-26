package no.nav.syfo.kafka.helsemelding

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.application.DialogmeldingProcessor
import no.nav.syfo.db.toList
import no.nav.syfo.dropData
import no.nav.syfo.model.DialogmeldingSource
import no.nav.syfo.testAppState
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.UUID

class HelsemeldingDialogmeldingConsumerTest {

    private val database = ExternalMockEnvironment.instance.database
    private val kafkaConsumer = mockk<KafkaConsumer<String, String>>()
    private val dialogmeldingProcessor = mockk<DialogmeldingProcessor>(relaxed = true)

    private val consumer = HelsemeldingDialogmeldingConsumer(
        kafkaConsumer = kafkaConsumer,
        applicationState = testAppState(),
        database = database,
        dialogmeldingProcessor = dialogmeldingProcessor,
    )

    private val topicPartition = TopicPartition(HELSEMELDING_DIALOGMELDING_TOPIC, 0)

    @BeforeEach
    fun beforeEach() {
        database.dropData()
        clearMocks(kafkaConsumer, dialogmeldingProcessor)
        justRun { kafkaConsumer.commitSync() }
    }

    private fun mockPoll(vararg values: String?) {
        val records = values.mapIndexed { index, value ->
            ConsumerRecord<String, String>(
                HELSEMELDING_DIALOGMELDING_TOPIC,
                topicPartition.partition(),
                index.toLong(),
                UUID.randomUUID().toString(),
                value,
            )
        }
        every { kafkaConsumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(topicPartition to records),
            emptyMap(),
        )
    }

    private data class LagretRad(
        val msgId: String,
        val source: String,
        val kafkaMelding: String?,
        val shaString: String?,
    )

    private fun hentLagredeRader(): List<LagretRad> =
        database.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT o.msg_id, o.source, o.kafka_melding::text AS kafka_melding, d.sha_string
                FROM DIALOGMELDINGOPPLYSNINGER o
                JOIN DIALOGMELDINGDOKUMENT d ON d.id = o.id
                """
            ).use {
                it.executeQuery().toList {
                    LagretRad(
                        msgId = getString("msg_id"),
                        source = getString("source"),
                        kafkaMelding = getString("kafka_melding"),
                        shaString = getString("sha_string"),
                    )
                }
            }
        }

    @Test
    fun `stores and processes new message, then commits`() {
        val msgId = UUID.randomUUID().toString()
        mockPoll(generateIncomingDialogMessageJson(id = msgId))

        runBlocking { consumer.pollAndProcess() }

        val rad = hentLagredeRader().single()
        assertEquals(msgId, rad.msgId)
        assertEquals(DialogmeldingSource.HELSEMELDING.name, rad.source)
        assertEquals(msgId, parseIncomingDialogMessage(rad.kafkaMelding!!).id)
        assertNull(rad.shaString)
        verify(exactly = 1) { kafkaConsumer.commitSync() }
        coVerify(exactly = 1) {
            dialogmeldingProcessor.process(match { it.msgId == msgId && it.source == DialogmeldingSource.HELSEMELDING })
        }
    }

    @Test
    fun `ignores duplicate message and does not process it again`() {
        val json = generateIncomingDialogMessageJson(id = UUID.randomUUID().toString())
        mockPoll(json)
        runBlocking { consumer.pollAndProcess() }
        clearMocks(dialogmeldingProcessor)

        mockPoll(json)
        runBlocking { consumer.pollAndProcess() }

        assertEquals(1, hentLagredeRader().size)
        verify(exactly = 2) { kafkaConsumer.commitSync() }
        coVerify(exactly = 0) { dialogmeldingProcessor.process(any()) }
    }

    @Test
    fun `ignores duplicate within the same batch`() {
        val json = generateIncomingDialogMessageJson(id = UUID.randomUUID().toString())
        mockPoll(json, json)

        runBlocking { consumer.pollAndProcess() }

        assertEquals(1, hentLagredeRader().size)
        coVerify(exactly = 1) { dialogmeldingProcessor.process(any()) }
    }

    @Test
    fun `skips invalid messages and commits`() {
        val gyldigMsgId = UUID.randomUUID().toString()
        mockPoll(
            "ikke json",
            null,
            """{"id": "mangler-felter"}""",
            generateIncomingDialogMessageJson(id = gyldigMsgId),
        )

        runBlocking { consumer.pollAndProcess() }

        assertEquals(listOf(gyldigMsgId), hentLagredeRader().map { it.msgId })
        verify(exactly = 1) { kafkaConsumer.commitSync() }
        coVerify(exactly = 1) { dialogmeldingProcessor.process(any()) }
    }

    @Test
    fun `commits even if processing fails`() {
        coEvery { dialogmeldingProcessor.process(any()) } throws RuntimeException("Feil")
        mockPoll(generateIncomingDialogMessageJson(id = UUID.randomUUID().toString()))

        runBlocking { consumer.pollAndProcess() }

        assertEquals(1, hentLagredeRader().size)
        verify(exactly = 1) { kafkaConsumer.commitSync() }
    }

    @Test
    fun `does not commit when storing fails`() {
        mockPoll(generateIncomingDialogMessageJson(id = UUID.randomUUID().toString()))
        val consumerMedFeilendeDatabase = HelsemeldingDialogmeldingConsumer(
            kafkaConsumer = kafkaConsumer,
            applicationState = testAppState(),
            database = mockk { every { connection } throws IllegalStateException("DB nede") },
            dialogmeldingProcessor = dialogmeldingProcessor,
        )

        assertThrows<IllegalStateException> {
            runBlocking { consumerMedFeilendeDatabase.pollAndProcess() }
        }
        verify(exactly = 0) { kafkaConsumer.commitSync() }
    }

    @Test
    fun `does nothing when poll returns no records`() {
        every { kafkaConsumer.poll(any<Duration>()) } returns ConsumerRecords.empty()

        runBlocking { consumer.pollAndProcess() }

        verify(exactly = 0) { kafkaConsumer.commitSync() }
    }
}
