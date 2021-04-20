package no.nav.syfo.rules

import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Status
import no.nav.syfo.validation.validatePersonAndDNumber
import no.nav.syfo.validation.validatePersonAndDNumber11Digits

enum class ValidationRuleChain(
    override val ruleId: Int?,
    override val status: Status,
    override val messageForUser: String,
    override val messageForSender: String,
    override val predicate: (RuleData<RuleMetadata>) -> Boolean
) : Rule<RuleData<RuleMetadata>> {

    UGYLDIG_FNR_LENGDE_PASIENT(
            1002,
            Status.INVALID,
            "Pasienten sitt fødselsnummer eller D-nummer er ikke 11 tegn.",
            "Pasienten sitt fødselsnummer eller D-nummer er ikke 11 tegn.", { (_, metadata) ->
        !validatePersonAndDNumber11Digits(metadata.patientPersonNumber)
    }),

    UGYLDIG_FNR_PASIENT(
            1006,
            Status.INVALID,
            "Fødselsnummer/D-nummer kan passerer ikke modulus 11",
            "Pasientens fødselsnummer/D-nummer er ikke gyldig", { (_, metadata) ->
        !validatePersonAndDNumber(metadata.patientPersonNumber)
    }),

    UGYLDIG_FNR_AVSENDER(
        1006,
        Status.INVALID,
        "Fødselsnummer for den som sendte dialogmeldingen, er ikke gyldig",
        "Avsenders fødselsnummer/D-nummer er ikke gyldig", { (_, metadata) ->
            !validatePersonAndDNumber(metadata.avsenderfnr)
        }),

    AVSENDER_FNR_ER_SAMME_SOM_PASIENT_FNR(
        9999,
        Status.INVALID,
        "Den som signert dialogmeldingen er også pasient.",
        "Avsender fnr er det samme som pasient fnr", { (_, metadata) ->
            metadata.avsenderfnr.equals(metadata.patientPersonNumber)
        }),
}
