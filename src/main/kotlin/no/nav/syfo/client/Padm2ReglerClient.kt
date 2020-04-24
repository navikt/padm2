package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult

@KtorExperimentalAPI
class Padm2ReglerClient(private val endpointUrl: String, private val client: HttpClient) {
    suspend fun executeRuleValidation(payload: ReceivedDialogmelding): ValidationResult = retry("padm2regler_validate") {
        client.post<ValidationResult>("$endpointUrl/v1/rules/validate") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            body = payload
        }
    }
}
