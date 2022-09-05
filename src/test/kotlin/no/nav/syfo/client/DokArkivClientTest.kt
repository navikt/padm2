package no.nav.syfo.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.azuread.v2.AzureAdV2Token
import no.nav.syfo.model.*
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.configure
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertFailsWith

internal class DokArkivClientTest {

    @MockK
    lateinit var azureAdV2Client: AzureAdV2Client

    val dokarkivClientId = "dokarkivClientId"

    @MockK
    lateinit var httpClient: HttpClient

    @MockK
    lateinit var httpStatement: HttpStatement

    @MockK
    lateinit var httpResponse: HttpResponse

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        coEvery {
            azureAdV2Client.getSystemToken(dokarkivClientId)
        } returns AzureAdV2Token(
            accessToken = "anyToken",
            expires = LocalDateTime.now().plusDays(1)
        )
    }

    fun createClient(statusCode: HttpStatusCode): HttpClient {
        return HttpClient(MockEngine) {
            install(ContentNegotiation) {
                jackson {
                    configure()
                }
            }
            expectSuccess = true
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
                        azureAdV2Client,
                        dokarkivClientId,
                        "Test",
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
                azureAdV2Client,
                dokarkivClientId,
                "Test",
                createClient(HttpStatusCode.OK)
            )
        val response = runBlocking {
            dokArkivClient
                .createJournalpost(journalpostRequest, LoggingMeta("1", "2", "3"))
        }

        response.journalpostId shouldBeEqualTo journalpostOKResponse.journalpostId
    }

    @Test
    fun `HTTP 201 fra journalpostoppretting returnerer JournalpostResponse`() {
        val dokArkivClient =
            DokArkivClient(
                azureAdV2Client,
                dokarkivClientId,
                "Test",
                createClient(HttpStatusCode.Created)
            )
        val response = runBlocking {
            dokArkivClient
                .createJournalpost(journalpostRequest, LoggingMeta("1", "2", "3"))
        }

        response.journalpostId shouldBeEqualTo journalpostOKResponse.journalpostId
    }

    @Test
    fun `JournalpostRequest gets AvsenderMottaker with 0-padded hpr as id if hpr is not null`() {
        val hprNrWithFiveDigits = "12345"

        val journalpostRequest = createJournalpostPayload(
            dialogmelding = dialogmelding,
            pdf = byteArrayOf(),
            avsenderFnr = "12345678901",
            avsenderHpr = hprNrWithFiveDigits,
            ediLoggId = "asd",
            signaturDato = LocalDateTime.now(),
            validationResult = ValidationResult(Status.OK, emptyList()),
            pasientFnr = "90876543219",
            vedleggListe = null,
        )

        journalpostRequest.avsenderMottaker!!.id shouldBeEqualTo "0000$hprNrWithFiveDigits"
        journalpostRequest.avsenderMottaker?.idType shouldBe IdType.HPR.value
        journalpostRequest.avsenderMottaker?.navn shouldBe dialogmelding.navnHelsepersonell
    }

    @Test
    fun `JournalpostRequest gets AvsenderMottaker without id, only name if hpr is null and avsenderFnr is not valid`() {
        val journalpostRequest = createJournalpostPayload(
            dialogmelding = dialogmelding,
            pdf = byteArrayOf(),
            avsenderFnr = "invalid",
            avsenderHpr = null,
            ediLoggId = "asd",
            signaturDato = LocalDateTime.now(),
            validationResult = ValidationResult(Status.OK, emptyList()),
            pasientFnr = "90876543219",
            vedleggListe = null,
        )

        journalpostRequest.avsenderMottaker?.id shouldBe null
        journalpostRequest.avsenderMottaker?.idType shouldBe null
        journalpostRequest.avsenderMottaker?.navn shouldBe dialogmelding.navnHelsepersonell
    }

    @Test
    fun `JournalpostRequest gets AvsenderMottaker with fnr as id if hpr is null and avsenderFnr is a valid dnr`() {
        val validDnr = "45088649080"

        val journalpostRequest = createJournalpostPayload(
            dialogmelding = dialogmelding,
            pdf = byteArrayOf(),
            avsenderFnr = validDnr,
            avsenderHpr = null,
            ediLoggId = "asd",
            signaturDato = LocalDateTime.now(),
            validationResult = ValidationResult(Status.OK, emptyList()),
            pasientFnr = "90876543219",
            vedleggListe = null,
        )

        journalpostRequest.avsenderMottaker?.id shouldBe validDnr
        journalpostRequest.avsenderMottaker?.idType shouldBe IdType.PERSON_IDENT.value
        journalpostRequest.avsenderMottaker?.navn shouldBe dialogmelding.navnHelsepersonell
    }

    val dialogmelding = Dialogmelding(
        id = "1234",
        innkallingMoterespons = null,
        foresporselFraSaksbehandlerForesporselSvar = null,
        henvendelseFraLegeHenvendelse = null,
        navnHelsepersonell = "Lego Legesen",
        signaturDato = LocalDateTime.now()
    )

    val journalpostRequest = JournalpostRequest(
        dokumenter = emptyList(),
        journalpostType = JournalpostType.INNGAAENDE.value,
    )

    val journalpostOKResponse = JournalpostResponse(
        listOf(DokumentInfo("1", "2", "Et dokument")),
        "123",
        true,
        "status",
        "melding"
    )

    val journalpostOKResponseAsJson = jacksonObjectMapper().writeValueAsString(journalpostOKResponse)
}
