package no.nav.syfo.application.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.access.APIConsumerAccessService
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.util.getBearerHeader
import java.util.UUID

const val vedleggSystemApiV1Path = "/api/system/v1/vedlegg"
const val vedleggSystemApiMsgIdParam = "msgid"

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
            val msgId = UUID.fromString(call.parameters[vedleggSystemApiMsgIdParam])
            // TODO: Hent ut vedlegg
            call.respond(HttpStatusCode.OK)
        }
    }
}
