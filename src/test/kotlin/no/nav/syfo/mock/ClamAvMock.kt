package no.nav.syfo.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.client.ScanResult
import no.nav.syfo.client.ScanStatus

val clamAvSuccessResponse = listOf(ScanResult(filename = "file1", result = ScanStatus.OK))
val clamAvVirusFoundResponse = listOf(ScanResult(filename = "file1", result = ScanStatus.FOUND))

suspend fun MockRequestHandleScope.clamAvMockResponse(request: HttpRequestData): HttpResponseData {
    val bodyText = request.body.toByteArray().decodeToString()
    return if (bodyText.contains("""filename="problem file"""")) {
        respond(clamAvVirusFoundResponse)
    } else {
        respond(clamAvSuccessResponse)
    }
}
