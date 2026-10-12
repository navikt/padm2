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
        extractValidVedlegg(safeUnmarshal(receivedDialogmelding.fellesformat)).map { it.toVedlegg() }
}
