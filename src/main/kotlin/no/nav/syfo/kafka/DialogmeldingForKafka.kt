package no.nav.syfo.kafka

import no.nav.syfo.model.Dialogmelding
import java.time.LocalDateTime

data class DialogmeldingForKafka(
    val msgId: String,
    val msgType: String,
    val navLogId: String,
    val mottattTidspunkt: LocalDateTime,
    val conversationRef: String?,
    val parentRef: String?,
    val personIdentPasient: String,
    val pasientAktoerId: String? = null, // deprecated
    val personIdentBehandler: String,
    val behandlerAktoerId: String? = null, // deprecated
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorReshId: String? = null, // deprecated
    val legekontorOrgName: String,
    val legehpr: String?,
    val dialogmelding: Dialogmelding,
    val antallVedlegg: Int,
    val journalpostId: String,
    val fellesformatXML: String,
)
