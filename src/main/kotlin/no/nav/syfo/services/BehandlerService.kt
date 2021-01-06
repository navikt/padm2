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

    // TODO Alle navnefeltene er nullable, trenger vi å sjekke det, eller skal vi satse på at det er greit?
    private fun navnFraBehandler(behandler: Behandler?): String =
        when {
            behandler == null -> {
                "Fant ikke navn" // TODO Dette må feile, fordi det betyr at personen ikke finnes i hpr. Da kommer den vel til å få status INVALID fra padm2regler, og det er kanskje mest naturlig at vi kjører løpet som vanlig, og får en INVALID-status til bruker til slutt?
            }
            behandler.mellomnavn == null -> {
                "${behandler.etternavn}, ${behandler.fornavn}"
            }
            else -> {
                "${behandler.etternavn}, ${behandler.fornavn} ${behandler.mellomnavn}"
            }
        }
}
