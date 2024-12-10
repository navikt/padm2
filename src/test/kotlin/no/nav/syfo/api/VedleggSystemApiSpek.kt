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
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*
import javax.jms.TextMessage

class VedleggSystemApiSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database
    val mqSender = mockk<MQSenderInterface>(relaxed = true)
    val dialogmeldingProducer = mockk<DialogmeldingProducer>(relaxed = true)
    val incomingMessage = mockk<TextMessage>(relaxed = true)
    val azureAdV2Client = mockk<AzureAdV2Client>(relaxed = true)

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

    fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
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

    beforeEachTest {
        database.dropData()
    }

    describe(VedleggSystemApiSpek::class.java.simpleName) {
        describe("Get vedlegg for msgId") {
            val msgId = UUID.randomUUID().toString()
            val url = "$vedleggSystemApiV1Path/$msgId"
            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.aadAppClient,
                azp = testIsBehandlerDialogClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            )

            describe("Happy path") {
                it("should get vedlegg for msgId") {
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
                        response.status shouldBe HttpStatusCode.OK
                        val vedleggListe = response.body<List<VedleggDTO>>()
                        vedleggListe.size shouldBeEqualTo 2
                    }
                }
                it("should only get valid vedlegg") {
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
                        response.status shouldBe HttpStatusCode.OK
                        val vedleggListe = response.body<List<VedleggDTO>>()
                        vedleggListe.size shouldBeEqualTo 1
                    }
                }
                it("should get plenty of vedlegg for msgId") {
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
                        response.status shouldBe HttpStatusCode.OK
                        val vedleggListe = response.body<List<VedleggDTO>>()
                        vedleggListe.size shouldBeEqualTo 45
                    }
                }
                it("should return 204 when unknown msgId") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(url) {
                            bearerAuth(validToken)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        }
                        response.status shouldBe HttpStatusCode.NoContent
                    }
                }
            }
            describe("Unhappy paths") {
                it("should return status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(url) {
                        }
                        response.status shouldBe HttpStatusCode.Unauthorized
                    }
                }

                it("should return status Forbidden if wrong azp") {
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
                        response.status shouldBe HttpStatusCode.Forbidden
                    }
                }
            }
        }
    }
})
