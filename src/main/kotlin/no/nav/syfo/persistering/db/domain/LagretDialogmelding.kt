package no.nav.syfo.persistering.db.domain

import no.nav.syfo.model.DialogmeldingSource

data class LagretDialogmelding(
    val id: String,
    val source: DialogmeldingSource,
    val fellesformat: String?,
)
