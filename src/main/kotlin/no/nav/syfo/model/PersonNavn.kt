package no.nav.syfo.model

data class PersonNavn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
) {
    fun etternavnFoerst(): String =
        if (mellomnavn == null) "$etternavn, $fornavn" else "$etternavn, $fornavn $mellomnavn"
}
