package no.nav.syfo.model

import java.time.LocalDateTime

data class ReceivedDialogmelding(
    val dialogmelding: Dialogmelding,
    val personNrPasient: String,
    val personNrLege: String,
    val navLogId: String,
    val msgId: String,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorReshId: String?,
    val legekontorOrgName: String,
    val mottattDato: LocalDateTime,
    val legehpr: String?,
    val fellesformat: String,
)
