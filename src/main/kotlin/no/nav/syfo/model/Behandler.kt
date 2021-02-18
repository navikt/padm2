package no.nav.syfo.model

data class Behandler(
    val etternavn: String,
    val fornavn: String,
    val mellomnavn: String?
)

fun Behandler.getName(): String = if (mellomnavn == null) "$etternavn, $fornavn" else "$etternavn, $fornavn $mellomnavn"
