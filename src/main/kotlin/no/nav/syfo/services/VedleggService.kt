package no.nav.syfo.services

import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.model.toVedlegg
import no.nav.syfo.util.extractValidVedlegg
import no.nav.syfo.util.safeUnmarshal

interface VedleggService {
    suspend fun hentVedlegg(receivedDialogmelding: ReceivedDialogmelding): List<Vedlegg>
}

class MqVedleggService : VedleggService {
    override suspend fun hentVedlegg(receivedDialogmelding: ReceivedDialogmelding): List<Vedlegg> =
        extractValidVedlegg(safeUnmarshal(receivedDialogmelding.requireFellesformat())).map { it.toVedlegg() }
}

/**
 * Vedlegg for meldinger fra Team Helsemelding hentes via deres API. Kommer i neste PR.
 */
class HelsemeldingVedleggService : VedleggService {
    override suspend fun hentVedlegg(receivedDialogmelding: ReceivedDialogmelding): List<Vedlegg> =
        error("Henting av vedlegg fra Team Helsemelding er ikke implementert")
}
