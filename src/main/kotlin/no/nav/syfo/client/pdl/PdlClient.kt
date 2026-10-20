package no.nav.syfo.client.pdl

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.azuread.v2.AzureAdV2Token
import no.nav.syfo.client.httpClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.model.PersonNavn
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class PdlClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val pdlClientId: String,
    private val pdlUrl: String,
    private val httpClient: HttpClient,
) {

    suspend fun personEksisterer(
        personIdent: PersonIdent,
    ): Boolean {
        val token = azureAdV2Client.getSystemToken(pdlClientId)
            ?: throw RuntimeException("Failed to send request to PDL: No token was found")
        return identer(
            ident = personIdent.value,
            token = token,
        )?.hentIdenter?.identer?.isNotEmpty() ?: false
    }

    suspend fun hentPersonNavn(
        personIdent: PersonIdent,
    ): PersonNavn {
        val token = azureAdV2Client.getSystemToken(pdlClientId)
            ?: throw RuntimeException("Failed to send request to PDL: No token was found")
        val request = PdlHentPersonRequest(
            query = getPdlQuery("/pdl/hentPerson.graphql"),
            variables = PdlHentPersonRequestVariables(ident = personIdent.value),
        )

        val response: HttpResponse = httpClient.post(pdlUrl) {
            setBody(request)
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
            header(BEHANDLINGSNUMMER_HEADER_KEY, BEHANDLINGSNUMMER_HEADER_VALUE)
        }

        if (response.status != HttpStatusCode.OK) {
            COUNT_CALL_PDL_IDENTER_FAIL.increment()
            throw RuntimeException("Request with url: $pdlUrl failed with response code ${response.status.value}")
        }
        val pdlPersonResponse = response.body<PdlPersonResponse>()
        if (!pdlPersonResponse.errors.isNullOrEmpty()) {
            COUNT_CALL_PDL_IDENTER_FAIL.increment()
            pdlPersonResponse.errors.forEach {
                logger.error("Error while requesting Person from PersonDataLosningen: ${it.message}")
            }
            throw RuntimeException("PDL returned errors for hentPerson")
        }
        val navn = pdlPersonResponse.data?.hentPerson?.navn?.firstOrNull()
        if (navn == null) {
            COUNT_CALL_PDL_IDENTER_FAIL.increment()
            throw RuntimeException("PDL returned no navn for person")
        }
        COUNT_CALL_PDL_IDENTER_SUCCESS.increment()
        return navn.toPersonNavn()
    }

    private suspend fun identer(
        ident: String,
        token: AzureAdV2Token,
    ): PdlHentIdenter? {
        val request = PdlHentIdenterRequest(
            query = getPdlQuery("/pdl/hentIdenter.graphql"),
            variables = PdlHentIdenterRequestVariables(
                ident = ident,
                historikk = false,
                grupper = listOf(
                    IdentType.AKTORID.name,
                    IdentType.FOLKEREGISTERIDENT.name
                )
            )
        )

        val response: HttpResponse = httpClient.post(pdlUrl) {
            setBody(request)
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
            header(BEHANDLINGSNUMMER_HEADER_KEY, BEHANDLINGSNUMMER_HEADER_VALUE)
            header(IDENTER_HEADER, IDENTER_HEADER)
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val pdlPersonReponse = response.body<PdlIdenterResponse>()
                if (pdlPersonReponse.errors.isNullOrEmpty()) {
                    COUNT_CALL_PDL_IDENTER_SUCCESS.increment()
                    pdlPersonReponse.data
                } else {
                    COUNT_CALL_PDL_IDENTER_FAIL.increment()
                    pdlPersonReponse.errors.forEach {
                        logger.error("Error while requesting Identer from PersonDataLosningen: ${it.message}")
                    }
                    null
                }
            }
            else -> {
                COUNT_CALL_PDL_IDENTER_FAIL.increment()
                throw RuntimeException("Request with url: $pdlUrl failed with response code ${response.status.value}")
            }
        }
    }

    private fun getPdlQuery(queryFilePath: String): String {
        return this::class.java.getResource(queryFilePath)
            .readText()
            .replace("[\n\r]", "")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PdlClient::class.java)
        const val IDENTER_HEADER = "identer"

        // Se behandlingskatalog https://behandlingskatalog.intern.nav.no/
        // Behandling: Sykefraværsoppfølging: Vurdere behov for oppfølging og rett til sykepenger etter §§ 8-4 og 8-8
        private const val BEHANDLINGSNUMMER_HEADER_KEY = "behandlingsnummer"
        private const val BEHANDLINGSNUMMER_HEADER_VALUE = "B426"
    }
}
