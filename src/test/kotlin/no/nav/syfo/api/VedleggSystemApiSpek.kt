package no.nav.syfo.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
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
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.services.EmottakService
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*
import javax.jms.TextMessage

class VedleggSystemApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    with(TestApplicationEngine()) {
        start()

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
            smtssClient = smtssClient,
            emottakService = emottakService,
        )

        val blockingApplicationRunner = BlockingApplicationRunner(
            applicationState = externalMockEnvironment.applicationState,
            database = database,
            inputconsumer = mockk(),
            mqSender = mqSender,
            dialogmeldingProcessor = dialogmeldingProcessor,
        )

        application.testApiModule(
            externalMockEnvironment = externalMockEnvironment,
        )

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
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            val vedleggListe = objectMapper.readValue<List<VedleggDTO>>(response.content!!)
                            vedleggListe.size shouldBeEqualTo 2
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                    }
                    it("should only get valid vedlegg") {
                        every { incomingMessage.text } returns(
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
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            val vedleggListe = objectMapper.readValue<List<VedleggDTO>>(response.content!!)
                            vedleggListe.size shouldBeEqualTo 1
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                    }
                    it("should get plenty of vedlegg for msgId") {
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
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            val vedleggListe = objectMapper.readValue<List<VedleggDTO>>(response.content!!)
                            vedleggListe.size shouldBeEqualTo 45
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                    }
                    it("should return 204 when unknown msgId") {
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.NoContent
                        }
                    }
                }
                describe("Unhappy paths") {
                    it("should return status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, url) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }

                    it("should return status Forbidden if wrong azp") {
                        val invalidToken = generateJWT(
                            audience = externalMockEnvironment.environment.aadAppClient,
                            azp = "isdialogmote-client-id",
                            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                        )
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(invalidToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }
                }
            }
        }
    }
})
