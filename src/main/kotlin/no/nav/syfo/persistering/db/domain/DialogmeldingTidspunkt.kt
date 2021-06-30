package no.nav.syfo.persistering.db.domain

import java.time.LocalDateTime

data class DialogmeldingTidspunkt(
    // Har signaturDato som String da denne ikke settes av oss, og kan i teorien ha ulik formatering
    val signaturDato: String,
    val mottattTidspunkt: LocalDateTime
)
