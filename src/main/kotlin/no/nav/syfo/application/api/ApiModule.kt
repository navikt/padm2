package no.nav.syfo.application.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.api.access.APIConsumerAccessService
import no.nav.syfo.application.api.authentication.*
import no.nav.syfo.client.wellknown.WellKnown
import no.nav.syfo.db.DatabaseInterface

fun Application.apiModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    wellKnownInternalAzureAD: WellKnown,
    environment: Environment,
) {
    installMetrics()
    installCallId()
    installContentNegotiation()
    installJwtAuthentication(
        jwtIssuerList = listOf(
            JwtIssuer(
                acceptedAudienceList = listOf(environment.aadAppClient),
                jwtIssuerType = JwtIssuerType.INTERNAL_AZUREAD,
                wellKnown = wellKnownInternalAzureAD,
            ),
        ),
    )
    installStatusPages()

    val apiConsumerAccessService = APIConsumerAccessService(
        azureAppPreAuthorizedApps = environment.aadAppPreAuthorizedApps,
    )

    routing {
        registerNaisApi(
            applicationState = applicationState,
        )
        authenticate(JwtIssuerType.INTERNAL_AZUREAD.name) {
            registerVedleggSystemApi(
                apiConsumerAccessService = apiConsumerAccessService,
                authorizedApplicationNames = environment.systemAPIAuthorizedConsumerApplicationNames,
                database = database,
            )
        }
    }
}
