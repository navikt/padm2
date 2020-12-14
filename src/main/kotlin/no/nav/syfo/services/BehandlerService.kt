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

        return navnFraBehandler(behandler)
    }

    // TODO Alle navnene er nullable, trenger vi å sjekke det, eller skal vi satse på at det er greit?
    private fun navnFraBehandler(behandler: Behandler?): String =
        when {
            behandler == null -> {
                "Fant ikke navn"
            }
            behandler.mellomnavn == null -> {
                "${behandler.etternavn}, ${behandler.fornavn}"
            }
            else -> {
                "${behandler.etternavn}, ${behandler.fornavn} ${behandler.mellomnavn}"
            }
        }
}
