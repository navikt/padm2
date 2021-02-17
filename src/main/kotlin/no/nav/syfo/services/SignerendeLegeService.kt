package no.nav.syfo.services

import io.ktor.util.*
import no.nav.syfo.client.HelsenettProxyBehandler
import no.nav.syfo.client.SyfohelsenettproxyClient
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class SignerendeLegeService(
    private val syfohelsenettproxyClient: SyfohelsenettproxyClient
) {
    suspend fun signerendeLegeNavn(
        signerendeLegeFnr: String,
        msgId: String,
        loggingMeta: LoggingMeta
    ): String {
        val signerendeLege = syfohelsenettproxyClient.finnBehandler(signerendeLegeFnr, msgId, loggingMeta)

        return getNameSignerendeLege(signerendeLege, signerendeLegeFnr)
    }

    private fun getNameSignerendeLege(helsenettProxyBehandler: HelsenettProxyBehandler?, behandlerFnr: String): String =
        when {
            helsenettProxyBehandler == null -> {
                behandlerFnr
            }
            helsenettProxyBehandler.fornavn == null && helsenettProxyBehandler.mellomnavn == null && helsenettProxyBehandler.etternavn == null -> {
                behandlerFnr
            }
            helsenettProxyBehandler.mellomnavn == null -> {
                "${helsenettProxyBehandler.etternavn}, ${helsenettProxyBehandler.fornavn}"
            }
            else -> {
                "${helsenettProxyBehandler.etternavn}, ${helsenettProxyBehandler.fornavn} ${helsenettProxyBehandler.mellomnavn}"
            }
        }
}
