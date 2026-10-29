package no.nav.syfo.services

import no.nav.syfo.client.helsemelding.AttachmentDTO
import no.nav.syfo.client.helsemelding.AttachmentServiceClient
import no.nav.syfo.kafka.helsemelding.parseIncomingDialogMessage
import no.nav.syfo.metrics.INVALID_PDF_VEDLEGG
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.model.erGyldig
import no.nav.syfo.model.toVedlegg
import no.nav.syfo.util.extractValidVedlegg
import no.nav.syfo.util.safeUnmarshal
import java.util.Base64

interface VedleggService {
    suspend fun hentVedlegg(receivedDialogmelding: ReceivedDialogmelding): List<Vedlegg>
}

class MqVedleggService : VedleggService {
    override suspend fun hentVedlegg(receivedDialogmelding: ReceivedDialogmelding): List<Vedlegg> =
        extractValidVedlegg(safeUnmarshal(receivedDialogmelding.requireFellesformat())).map { it.toVedlegg() }
}

class HelsemeldingVedleggService(
    private val attachmentServiceClient: AttachmentServiceClient,
) : VedleggService {
    override suspend fun hentVedlegg(receivedDialogmelding: ReceivedDialogmelding): List<Vedlegg> {
        val kafkaMelding = requireNotNull(receivedDialogmelding.kafkaMelding) {
            "Dialogmelding ${receivedDialogmelding.dialogmelding.id} fra ${receivedDialogmelding.source} mangler kafkaMelding"
        }
        val antallVedlegg = parseIncomingDialogMessage(kafkaMelding).numberOfAttachments
        return if (antallVedlegg == 0) {
            emptyList()
        } else {
            val alleVedlegg = attachmentServiceClient.hentVedlegg(messageId = receivedDialogmelding.msgId)
                .map { it.toVedlegg() }
            if (alleVedlegg.size != antallVedlegg) {
                throw IllegalStateException(
                    "Fikk ${alleVedlegg.size} vedlegg fra attachment-service, forventet $antallVedlegg for msgId ${receivedDialogmelding.msgId}"
                )
            }
            alleVedlegg.filter { it.erGyldig() }.also {
                if (it.size < alleVedlegg.size) {
                    INVALID_PDF_VEDLEGG.increment()
                }
            }
        }
    }
}

private fun AttachmentDTO.toVedlegg(): Vedlegg =
    Vedlegg(
        mimeType = contentType,
        beskrivelse = description,
        contentBase64 = Base64.getDecoder().decode(contentBase64),
    )
