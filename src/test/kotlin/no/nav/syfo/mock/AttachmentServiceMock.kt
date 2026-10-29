package no.nav.syfo.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.helsemelding.AttachmentDTO
import java.util.Base64

const val MSG_ID_MED_VEDLEGG = "3fa85f64-5717-4562-b3fc-2c963f66afa6"

val gyldigPdf = "%PDF-1.4 innhold".toByteArray()
val pngBilde = byteArrayOf(1, 2, 3)

// Den siste er ugyldig, siden innholdet ikke starter med %PDF-
val vedleggFraAttachmentService = listOf(
    AttachmentDTO(
        description = "Legeerklæring",
        contentType = "application/pdf",
        contentBase64 = Base64.getEncoder().encodeToString(gyldigPdf),
    ),
    AttachmentDTO(
        description = "Bilde",
        contentType = "image/png",
        contentBase64 = Base64.getEncoder().encodeToString(pngBilde),
    ),
    AttachmentDTO(
        description = "Ødelagt pdf",
        contentType = "application/pdf",
        contentBase64 = Base64.getEncoder().encodeToString("ikke pdf".toByteArray()),
    ),
)

fun MockRequestHandleScope.attachmentServiceMockResponse(request: HttpRequestData): HttpResponseData =
    when (request.url.segments.last()) {
        MSG_ID_MED_VEDLEGG -> respond(vedleggFraAttachmentService)
        else -> respond(content = "", status = HttpStatusCode.NotFound, headers = headersOf())
    }
