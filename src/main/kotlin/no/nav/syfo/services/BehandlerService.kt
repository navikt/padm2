package no.nav.syfo.services

import io.ktor.util.*
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.SyfohelsenettproxyClient
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class BehandlerService(
    private val syfohelsenettproxyClient: SyfohelsenettproxyClient
) {
    suspend fun behandlernavn(
        behandlerFnr: String,
        msgId: String,
        loggingMeta: LoggingMeta
    ): String {
        val behandler = syfohelsenettproxyClient.finnBehandler(behandlerFnr, msgId, loggingMeta)

        return navnFraBehandler(behandler, behandlerFnr)
    }

    private fun navnFraBehandler(behandler: Behandler?, behandlerFnr: String): String =
        when {
            behandler == null -> {
                behandlerFnr
            }
            behandler.fornavn == null && behandler.mellomnavn == null && behandler.etternavn == null -> {
                behandlerFnr
            }
            behandler.mellomnavn == null -> {
                "${behandler.etternavn}, ${behandler.fornavn}"
            }
            else -> {
                "${behandler.etternavn}, ${behandler.fornavn} ${behandler.mellomnavn}"
            }
        }
}
