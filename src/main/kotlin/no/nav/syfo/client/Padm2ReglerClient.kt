package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import java.io.IOException

@KtorExperimentalAPI
class Padm2ReglerClient(private val endpointUrl: String, private val client: HttpClient) {
    suspend fun executeRuleValidation(payload: ReceivedDialogmelding): ValidationResult = retry("padm2regler_validate") {
        val response: HttpResponse = client.post("$endpointUrl/v1/rules/validate") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            body = payload
        }

        when(response.status) {
            HttpStatusCode.InternalServerError -> throw RuntimeException("Fikk en feil fra padm2regler som ikke vi ikke kjører retry for ${response.content}")
            HttpStatusCode.ServiceUnavailable -> throw IOException("Fikk en feil fra padm2regler som kan retryes ${response.content}")
            HttpStatusCode.OK -> response.receive()
            else -> throw IOException("Vi fikk en uventet feil fra padm2regler, prøver på nytt! ${response.content}")
        }
    }
}
