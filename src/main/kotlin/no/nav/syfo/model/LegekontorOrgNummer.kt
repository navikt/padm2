package no.nav.syfo.model

@JvmInline
value class LegekontorOrgNummer(val value: String) {
    init {
        require(Regex("^\\d{9}\$").matches(value)) { "LegekontorOrgNummer must be a 9-digit number" }
    }
}
