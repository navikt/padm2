package no.nav.syfo.services

import no.nav.syfo.client.ClamAvClient
import no.nav.syfo.client.ScanStatus
import no.nav.syfo.logger
import no.nav.syfo.model.Vedlegg

class VirusScanService(
    private val clamAvClient: ClamAvClient
) {
    suspend fun vedleggContainsVirus(vedlegg: List<Vedlegg>): Boolean {
        return if (vedlegg.isEmpty()) {
            false
        } else {
            logger.info("Scanning vedlegg for virus, numbers of vedlegg: " + vedlegg.size)
            val scanResultMayContainVirus = clamAvClient.virusScanVedlegg(vedlegg).filter { it.result != ScanStatus.OK }
            scanResultMayContainVirus.map {
                logger.warn("Vedlegg may contain virus, filename: " + it.filename)
            }
            scanResultMayContainVirus.isNotEmpty()
        }
    }
}
