package no.nav.syfo.client.pdl

import no.nav.syfo.model.PersonNavn
import java.util.Locale

data class PdlPersonResponse(
    val errors: List<PdlError>?,
    val data: PdlHentPerson?,
)

data class PdlHentPerson(
    val hentPerson: PdlPerson?,
)

data class PdlPerson(
    val navn: List<PdlNavn>,
)

data class PdlNavn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

fun PdlNavn.toPersonNavn(): PersonNavn = PersonNavn(
    fornavn = fornavn.toNavnFormat(),
    mellomnavn = mellomnavn?.takeIf { it.isNotBlank() }?.toNavnFormat(),
    etternavn = etternavn.toNavnFormat(),
)

// PDL returnerer navn med store bokstaver, f.eks. "OLA-PER NORDMANN".
private fun String.toNavnFormat(): String =
    lowercase(Locale.forLanguageTag("no")).split(" ").joinToString(" ") { ord ->
        ord.split("-").joinToString("-") { del -> del.replaceFirstChar { it.titlecase(Locale.forLanguageTag("no")) } }
    }
