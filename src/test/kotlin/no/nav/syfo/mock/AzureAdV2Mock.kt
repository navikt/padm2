package no.nav.syfo.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpResponseData
import no.nav.syfo.UserConstants
import no.nav.syfo.client.azuread.v2.AzureAdV2TokenResponse

fun MockRequestHandleScope.azureAdV2MockResponse(): HttpResponseData =
    respond(
        AzureAdV2TokenResponse(
            access_token = UserConstants.AZUREAD_TOKEN,
            expires_in = 3600,
            token_type = "type",
        )
    )
