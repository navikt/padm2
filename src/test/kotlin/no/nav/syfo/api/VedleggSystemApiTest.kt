package no.nav.syfo.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.*
import no.nav.syfo.application.BlockingApplicationRunner
import no.nav.syfo.application.DialogmeldingProcessor
import no.nav.syfo.application.api.VedleggDTO
import no.nav.syfo.application.api.vedleggSystemApiV1Path
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.util.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*
import javax.jms.TextMessage

class VedleggSystemApiTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val mqSender = mockk<MQSenderInterface>(relaxed = true)
    private val dialogmeldingProducer = mockk<DialogmeldingProducer>(relaxed = true)
    private val incomingMessage = mockk<TextMessage>(relaxed = true)
    private val azureAdV2Client = mockk<AzureAdV2Client>(relaxed = true)

    private val dialogmeldingProcessor = DialogmeldingProcessor(
        database = database,
        env = externalMockEnvironment.environment,
        mqSender = mqSender,
        dialogmeldingProducer = dialogmeldingProducer,
        azureAdV2Client = azureAdV2Client,
    )

    private val blockingApplicationRunner = BlockingApplicationRunner(
        applicationState = externalMockEnvironment.applicationState,
        database = database,
        inputconsumer = mockk(),
        mqSender = mqSender,
        dialogmeldingProcessor = dialogmeldingProcessor,
    )

    private fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
        application {
            testApiModule(
                externalMockEnvironment = externalMockEnvironment,
            )
        }
        val client = createClient {
            install(ContentNegotiation) {
                jackson { configure() }
            }
        }
        return client
    }

    @BeforeEach
    fun beforeEach() {
        database.dropData()
    }

    private val msgId = UUID.randomUUID().toString()
    private val url = "$vedleggSystemApiV1Path/$msgId"
    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.aadAppClient,
        azp = testIsBehandlerDialogClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
    )

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {

        @Test
        fun `should get vedlegg for msgId`() {
            testApplication {
                val client = setupApiAndClient()
                every { incomingMessage.text } returns(
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
                        .replace(
                            "<MsgId>37340D30-FE14-42B5-985F-A8FF8FFA0CB5</MsgId>",
                            "<MsgId>$msgId</MsgId>",
                        )
                    )
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                val response = client.get(url) {
                    bearerAuth(validToken)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val vedleggListe = response.body<List<VedleggDTO>>()
                assertEquals(2, vedleggListe.size)
            }
        }

        @Test
        fun `should only get valid vedlegg`() {
            testApplication {
                val client = setupApiAndClient()
                every { incomingMessage.text } returns (
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat_vedlegg.xml")
                        .replace(
                            "<MimeType>image/jpeg</MimeType>",
                            "<MimeType>application/pdf</MimeType>",
                        )
                        .replace(
                            "<MsgId>37340D30-FE14-42B5-985F-A8FF8FFA0CB5</MsgId>",
                            "<MsgId>$msgId</MsgId>",
                        )
                    )
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                val response = client.get(url) {
                    bearerAuth(validToken)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val vedleggListe = response.body<List<VedleggDTO>>()
                assertEquals(1, vedleggListe.size)
            }
        }

        @Test
        fun `should get plenty of vedlegg for msgId`() {
            testApplication {
                val client = setupApiAndClient()
                every { incomingMessage.text } returns(
                    getFileAsString("src/test/resources/dialogmelding_dialog_notat_veldig_mange_vedlegg.xml")
                        .replace(
                            "<MsgId>37340D30-FE14-42B5-985F-A8FF8FFA0CB5</MsgId>",
                            "<MsgId>$msgId</MsgId>",
                        )
                    )
                runBlocking {
                    blockingApplicationRunner.processMessage(incomingMessage)
                }
                val response = client.get(url) {
                    bearerAuth(validToken)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val vedleggListe = response.body<List<VedleggDTO>>()
                assertEquals(45, vedleggListe.size)
            }
        }

        @Test
        fun `should return 204 when unknown msgId`() {
            testApplication {
                val client = setupApiAndClient()
                val response = client.get(url) {
                    bearerAuth(validToken)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                assertEquals(HttpStatusCode.NoContent, response.status)
            }
        }
    }

    @Nested
    @DisplayName("Unhappy paths")
    inner class UnhappyPaths {

        @Test
        fun `should return status Unauthorized if no token is supplied`() {
            testApplication {
                val client = setupApiAndClient()
                val response = client.get(url) {
                }
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }

        @Test
        fun `should return status Forbidden if wrong azp`() {
            testApplication {
                val client = setupApiAndClient()
                val invalidToken = generateJWT(
                    audience = externalMockEnvironment.environment.aadAppClient,
                    azp = "isdialogmote-client-id",
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                )
                val response = client.get(url) {
                    bearerAuth(invalidToken)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }
    }
}
