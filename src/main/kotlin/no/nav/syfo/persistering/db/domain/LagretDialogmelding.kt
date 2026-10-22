package no.nav.syfo.persistering.db.domain

import no.nav.syfo.kafka.helsemelding.receivedDialogmeldingFromHelsemelding
import no.nav.syfo.model.DialogmeldingSource
import no.nav.syfo.model.ReceivedDialogmelding

data class LagretDialogmelding(
    val id: String,
    val source: DialogmeldingSource,
    val fellesformat: String?,
    val kafkaMelding: String?,
) {
    fun toReceivedDialogmelding(): ReceivedDialogmelding =
        when (source) {
            DialogmeldingSource.EMOTTAK -> ReceivedDialogmelding.create(
                dialogmeldingId = id,
                fellesformat = requireNotNull(fellesformat) { "Dialogmelding $id fra $source mangler fellesformat" },
            )
            DialogmeldingSource.HELSEMELDING -> receivedDialogmeldingFromHelsemelding(
                dialogmeldingId = id,
                kafkaMelding = requireNotNull(kafkaMelding) { "Dialogmelding $id fra $source mangler kafka_melding" },
            )
        }
}
