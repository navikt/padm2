package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.json.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.runBlocking
import no.nav.syfo.model.DokumentInfo
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

@KtorExperimentalAPI
internal class DokArkivClientTest {

    @MockK
    lateinit var httpClient: HttpClient

    @MockK
    lateinit var httpStatement: HttpStatement

    @MockK
    lateinit var httpResponse: HttpResponse

    @MockK
    lateinit var stsClient: StsOidcClient

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        coEvery { stsClient.oidcToken() } returns OidcToken("test", "test", 9999L)
    }

    fun createClient(statusCode: HttpStatusCode): HttpClient {
        return HttpClient(MockEngine) {
            install(JsonFeature) {
                serializer = JacksonSerializer {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
            engine {
                addHandler {
                    val responseHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())
                    when (statusCode) {
                        HttpStatusCode.OK -> respond(
                            journalpostOKResponseAsJson,
                            HttpStatusCode.OK,
                            responseHeaders
                        )
                        HttpStatusCode.Created -> respond(
                            journalpostOKResponseAsJson,
                            HttpStatusCode.Created,
                            responseHeaders
                        )
                        else -> respondError(statusCode, statusCode.description, responseHeaders)
                    }
                }
            }
        }
    }

    @Test
    fun `Feilkode fra journalpostoppretting kaster RuntimeException`() {
        assertFailsWith<RuntimeException>(
            message = "Fikk ikke exception",
            block = {
                val dokArkivClient =
                    DokArkivClient(
                        "Test",
                        stsClient,
                        createClient(HttpStatusCode.InternalServerError)
                    )
                runBlocking {
                    dokArkivClient
                        .createJournalpost(journalpostRequest, LoggingMeta("1", "2", "3"))
                }
            }
        )
    }

    @Test
    fun `HTTP 200 fra journalpostoppretting returnerer JournalpostResponse`() {
        val dokArkivClient =
            DokArkivClient(
                "Test",
                stsClient,
                createClient(HttpStatusCode.OK)
            )
        val response = runBlocking {
            dokArkivClient
                .createJournalpost(journalpostRequest, LoggingMeta("1", "2", "3"))
        }

        response.journalpostId shouldEqual journalpostOKResponse.journalpostId
    }

    @Test
    fun `HTTP 201 fra journalpostoppretting returnerer JournalpostResponse`() {
        val dokArkivClient =
            DokArkivClient(
                "Test",
                stsClient,
                createClient(HttpStatusCode.Created)
            )
        val response = runBlocking {
            dokArkivClient
                .createJournalpost(journalpostRequest, LoggingMeta("1", "2", "3"))
        }

        response.journalpostId shouldEqual journalpostOKResponse.journalpostId
    }

    val journalpostRequest = JournalpostRequest(dokumenter = emptyList())

    val journalpostOKResponse = JournalpostResponse(
        listOf(DokumentInfo("1", "2", "Et dokument")),
        "123",
        true,
        "status",
        "melding"
    )

    val journalpostOKResponseAsJson = jacksonObjectMapper().writeValueAsString(journalpostOKResponse)
}
