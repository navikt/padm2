package no.nav.syfo.client

import com.fasterxml.jackson.annotation.JsonAlias
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.model.Vedlegg
import java.io.IOException

class ClamAvClient(
    private val endpointUrl: String
) {
    suspend fun virusScanVedlegg(vedleggList: List<Vedlegg>): List<ScanResult> {
        val httpResponse: HttpResponse = httpClient.submitFormWithBinaryData(
            url = "$endpointUrl/scan",
            formData = formData {
                vedleggList.forEachIndexed { index, vedlegg ->
                    append(
                        key = "file$index",
                        value = vedlegg.contentBase64,
                        headers = Headers.build {
                            append(HttpHeaders.ContentType, vedlegg.mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=${vedlegg.beskrivelse}")
                        }
                    )
                }
            }
        ) {
            accept(ContentType.Application.Json)
        }
        return when (httpResponse.status) {
            HttpStatusCode.OK -> httpResponse.body<List<ScanResult>>()
            else -> throw IOException("Vi fikk en uventet feil fra clamAV, prøver på nytt! ${httpResponse.bodyAsChannel()}")
        }
    }
}

data class ScanResult(
    @JsonAlias("Filename")
    val filename: String,
    @JsonAlias("Result")
    val result: ScanStatus,
)

enum class ScanStatus {
    FOUND, OK, ERROR
}
