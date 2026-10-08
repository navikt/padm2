package no.nav.syfo.model

data class Behandler(
    val etternavn: String,
    val fornavn: String,
    val mellomnavn: String?,
    val hprId: String? = null,
)

fun Behandler.getName(): String = if (mellomnavn == null) "$fornavn $etternavn" else "$fornavn $mellomnavn $etternavn"
