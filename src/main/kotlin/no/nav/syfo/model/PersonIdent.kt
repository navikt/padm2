package no.nav.syfo.domain

val elevenDigits = Regex("^\\d{11}\$")

data class PersonIdent(val value: String) {
    init {
        if (!elevenDigits.matches(value)) {
            throw IllegalArgumentException("Value is not a valid PersonIdent")
        }
    }
}
