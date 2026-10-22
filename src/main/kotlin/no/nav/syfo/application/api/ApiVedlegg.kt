package no.nav.syfo.application.api

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.access.APIConsumerAccessService
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.DialogmeldingSource
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.model.erGyldig
import no.nav.syfo.model.toPDFVedlegg
import no.nav.syfo.model.toVedlegg
import no.nav.syfo.persistering.db.domain.LagretDialogmelding
import no.nav.syfo.persistering.db.hentLagretDialogmelding
import no.nav.syfo.persistering.db.hentVedlegg
import no.nav.syfo.util.*

const val vedleggSystemApiV1Path = "/api/system/v1/vedlegg"
const val vedleggSystemApiMsgIdParam = "msgid"
val mapper = configuredJacksonMapper()

fun Route.registerVedleggSystemApi(
    apiConsumerAccessService: APIConsumerAccessService,
    authorizedApplicationNames: List<String>,
    database: DatabaseInterface,
) {
    route(vedleggSystemApiV1Path) {
        get("/{$vedleggSystemApiMsgIdParam}") {
            val token = this.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to retrieve vedlegg: No token supplied in request header")
            apiConsumerAccessService.validateConsumerApplicationAZP(
                authorizedApplicationNames = authorizedApplicationNames,
                token = token,
            )
            val msgId = call.parameters[vedleggSystemApiMsgIdParam]
            val lagretDialogmelding = msgId?.let {
                database.hentLagretDialogmelding(it)
            }
            val vedlegg = lagretDialogmelding?.let { hentGyldigeVedlegg(database, it) }
            if (vedlegg == null) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respondBytes(
                    contentType = ContentType.Application.Json,
                    bytes = mapper.writeValueAsBytes(
                        vedlegg
                            .map { it.toPDFVedlegg() }
                            .map { VedleggDTO(it.contentBase64) }
                    ),
                )
            }
        }
    }
}

private fun hentGyldigeVedlegg(
    database: DatabaseInterface,
    lagretDialogmelding: LagretDialogmelding,
): List<Vedlegg>? =
    when (lagretDialogmelding.source) {
        DialogmeldingSource.EMOTTAK ->
            lagretDialogmelding.fellesformat
                ?.takeIf { it.isNotEmpty() }
                ?.let { fellesformat -> extractValidVedlegg(safeUnmarshal(fellesformat)).map { it.toVedlegg() } }
        DialogmeldingSource.HELSEMELDING ->
            database.hentVedlegg(lagretDialogmelding.id).filter { it.erGyldig() }
    }

data class VedleggDTO(
    val bytes: ByteArray,
)
