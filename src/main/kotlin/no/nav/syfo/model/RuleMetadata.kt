package no.nav.syfo.model

import java.time.LocalDateTime

data class RuleMetadata(
    val signatureDate: LocalDateTime,
    val receivedDate: LocalDateTime,
    val innbyggerident: String,
    val legekontorOrgnr: String?,
    val avsenderfnr: String
)
