package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.metrics.CASES_CREATED
import no.nav.syfo.model.OpprettSak
import no.nav.syfo.model.SakResponse
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class SakClient constructor(
    val url: String,
    val oidcClient: StsOidcClient,
    val httpClient: HttpClient
) {
    private suspend fun createSak(
        pasientAktoerId: String,
        msgId: String
    ): SakResponse = retry("sak_opprett") {
        httpClient.post<SakResponse>(url) {
            contentType(ContentType.Application.Json)
            header("X-Correlation-ID", msgId)
            header("Authorization", "Bearer ${oidcClient.oidcToken().access_token}")
            body = OpprettSak(
                tema = "OPP",
                applikasjon = "FS22",
                aktoerId = pasientAktoerId,
                orgnr = null,
                fagsakNr = null
            )
        }
    }

    private suspend fun findSak(
        pasientAktoerId: String,
        msgId: String
    ): List<SakResponse>? = retry("finn_sak") {
        httpClient.get<List<SakResponse>?>(url) {
            contentType(ContentType.Application.Json)
            header("X-Correlation-ID", msgId)
            header("Authorization", "Bearer ${oidcClient.oidcToken().access_token}")
            parameter("tema", "OPP")
            parameter("aktoerId", pasientAktoerId)
            parameter("applikasjon", "AO01")
        }
    }

    suspend fun findOrCreateSak(
        pasientAktoerId: String,
        msgId: String,
        loggingMeta: LoggingMeta
    ): SakResponse {
        val findSakResponse = findSak(pasientAktoerId, msgId)

        return if (findSakResponse.isNullOrEmpty()) {
            createSak(pasientAktoerId, msgId).also {
                CASES_CREATED.inc()
                log.info("Opprettet en sak med sakid: {}, {}", it.id, fields(loggingMeta))
            }
        } else {
            findSakResponse.sortedBy { it.opprettetTidspunkt }.last().also {
                log.info("Fant en sak med sakid: {}, {}", it.id, fields(loggingMeta))
            }
        }
    }
}
