package no.nav.syfo.model

data class Behandler(
    val etternavn: String,
    val fornavn: String,
    val mellomnavn: String?
)

fun Behandler.getName(): String = if (mellomnavn == null) "$fornavn $etternavn" else "$fornavn $mellomnavn $etternavn"
