package no.nav.syfo.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JournalpostRequest(
    val datoMottatt: String? = null,
    val eksternReferanseId: String? = null,
    val tittel: String? = null,
    val journalpostType: String,
    val tema: String? = null,
    val behandlingstema: String? = null,
    val kanal: String? = null,
    val journalfoerendeEnhet: String? = null,
    val avsenderMottaker: AvsenderMottaker? = null,
    var bruker: Bruker? = null,
    val sak: Sak? = null,
    val dokumenter: List<Dokument>,
)

enum class IdType(
    val value: String,
) {
    PERSON_IDENT("FNR"),
    HPR("HPRNR"),
}

enum class JournalpostType(
    val value: String,
) {
    INNGAAENDE("INNGAAENDE"),
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AvsenderMottaker(
    val id: String? = null,
    val idType: String? = null,
    val navn: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Bruker(
    val id: String,
    val idType: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Dokument(
    val tittel: String,
    val brevkode: String? = null,
    val dokumentvarianter: List<Dokumentvarianter>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Dokumentvarianter(
    val filnavn: String,
    val filtype: String,
    val fysiskDokument: ByteArray,
    val variantformat: String,
)

enum class SaksType(
    val value: String,
) {
    GENERELL("GENERELL_SAK"),
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Sak(
    val fagsakId: String? = null,
    val fagsaksystem: String? = null,
    val sakstype: String,
)
