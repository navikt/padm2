package no.nav.syfo.model

data class Behandler(
    val fnr: String? = null,
    val hprId: String? = null,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?,
)

const val UKJENT_BEHANDLER_NAVN = "Ukjent"

fun Behandler.getNavn(
    etternavnFoerst: Boolean = false,
    fallback: String,
): String {
    val fornavnOgMellomnavn = listOf(fornavn, mellomnavn).filterNot { it.isNullOrBlank() }.joinToString(" ")
    return when {
        etternavn.isNullOrBlank() -> fornavnOgMellomnavn.ifEmpty { fallback }
        fornavnOgMellomnavn.isEmpty() -> etternavn
        etternavnFoerst -> "$etternavn, $fornavnOgMellomnavn"
        else -> "$fornavnOgMellomnavn $etternavn"
    }
}
