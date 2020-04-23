package no.nav.syfo.model

import no.nav.helse.dialogmelding.CV
import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.helse.dialogmelding.XMLForesporsel
import no.nav.helse.dialogmelding.XMLNotat

fun XMLDialogmelding.toDialogmelding(
    dialogmeldingId: String,
    dialogmeldingType: DialogmeldingType
) = Dialogmelding(
    id = dialogmeldingId,
    dialogmeldingNotat = if (dialogmeldingType == DialogmeldingType.DIALOGMELDING_HENVENDELSE_FRA_LEGE) {
        notat.firstOrNull()?.toDialogmeldingNotat()
    } else {
        null
    },
    dialogmeldingSvar = if (dialogmeldingType == DialogmeldingType.DIALOGMELDING_SVAR_FORESPORSEL_OM_PASIENT) {
        notat.firstOrNull()?.toDialogmeldingSvar()
    } else {
        null
    },
    dialogmeldingForesporsel = if (dialogmeldingType == DialogmeldingType.DIALOGMELDING_SVAR_INNKALLING_DIALOGMOTE) {
        notat.firstOrNull()?.toDialogmeldingForesporsel()
    } else {
        null
    }

)

fun XMLNotat.toDialogmeldingNotat(): DialogmeldingNotat {

    return DialogmeldingNotat(
        teamakode = temaKodet.toTeamakode(),
        tekstNotatInnhold = tekstNotatInnhold.toString(),
        dokIdNotat = dokIdNotat,
        datoNotat = datoNotat.toGregorianCalendar().toZonedDateTime().toLocalDate()
    )
}

fun XMLNotat.toDialogmeldingSvar(): DialogmeldingSvar {

    return DialogmeldingSvar(
        teamakode = temaKodet.toTeamakode(),
        tekstNotatInnhold = tekstNotatInnhold.toString(),
        dokIdNotat = dokIdNotat,
        foresporsel = foresporsel.toForesporsel()
    )
}

fun XMLNotat.toDialogmeldingForesporsel(): DialogmeldingForesporsel {

    return DialogmeldingForesporsel(
        teamakode = temaKodet.toTeamakode(),
        tekstNotatInnhold = tekstNotatInnhold.toString()
    )
}

fun CV.toTeamakode(): TemaKode {
    return TemaKode(dn, ot, s, v)
}

fun XMLForesporsel.toForesporsel(): Foresporsel {

    return Foresporsel(
        typeForesp = TypeForesp(typeForesp.dn, typeForesp.s, typeForesp.v),
        sporsmal = sporsmal,
        dokIdForesp = dokIdForesp,
        rollerRelatertNotat = RollerRelatertNotat(
            rolleNotat = RolleNotat(rollerRelatertNotat.first().rolleNotat.s, rollerRelatertNotat.first().rolleNotat.v),
            person = Person(rollerRelatertNotat.first().person.givenName, rollerRelatertNotat.first().person.familyName)
        )
    )
}
