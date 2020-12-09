package no.nav.syfo.model

data class PdfModel(
    val dialogmelding: Dialogmelding,
    val validationResult: ValidationResult,
    val pasientFnr: String,
    val pasientNavn: String
)
