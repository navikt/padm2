package no.nav.syfo.model

import no.nav.helse.dialogmelding.CV
import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.helse.dialogmelding.XMLForesporsel
import no.nav.helse.dialogmelding.XMLNotat
import no.nav.helse.msgHead.XMLHealthcareProfessional

fun XMLDialogmelding.toDialogmelding(
    dialogmeldingId: String
) = Dialogmelding(
        id = dialogmeldingId,
        dialogmeldingNotat = true,
        dialogmeldingSvar = false,
        notat = notat.first().toNotat()

)

fun XMLNotat.toNotat(): Notat {

        return Notat(
                teamakode = temaKodet.toTeamakode(),
                tekstNotatInnhold = tekstNotatInnhold.toString(),
                dokIdNotat = dokIdNotat,
                foresporsel = foresporsel.toForesporsel()
        )
}

fun CV.toTeamakode(): TemaKode {
        return TemaKode(dn, ot, s, v)
}

fun XMLForesporsel.toForesporsel(): Foresporsel {

        return Foresporsel(
                typeForesp = TypeForesp(typeForesp.dn, typeForesp.s, typeForesp.v),
                sporsmal = "",
                dokIdForesp = "",
                rollerRelatertNotat = RollerRelatertNotat(
                        rolleNotat = RolleNotat(rollerRelatertNotat.first().rolleNotat.s, rollerRelatertNotat.first().rolleNotat.v),
                        person = Person(rollerRelatertNotat.first().person.givenName, rollerRelatertNotat.first().person.familyName)
                )
        )
}

fun XMLHealthcareProfessional.formatName(): String = if (middleName == null) {
        "${familyName.toUpperCase()} ${givenName.toUpperCase()}"
} else {
        "${familyName.toUpperCase()} ${givenName.toUpperCase()} ${middleName.toUpperCase()}"
}
