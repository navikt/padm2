package no.nav.syfo.model

import java.time.LocalDateTime
import no.nav.helse.dialogmelding.CV
import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.helse.dialogmelding.XMLForesporsel
import no.nav.helse.dialogmelding.XMLNotat

fun XMLDialogmelding.toDialogmelding(
    dialogmeldingId: String,
    dialogmeldingType: DialogmeldingType,
    signaturDato: LocalDateTime,
    navnHelsePersonellNavn: String?
) = Dialogmelding(
    id = dialogmeldingId,
    dialogmeldingHenvendelseFraLege = if (dialogmeldingType == DialogmeldingType.DIALOGMELDING_HENVENDELSE_FRA_LEGE) {
        notat.firstOrNull()?.toDialogmeldingHenvendelseFraLege()
    } else {
        null
    },
    dialogmeldingInnkallingDialogmote = if (dialogmeldingType == DialogmeldingType.DIALOGMELDING_SVAR_INNKALLING_DIALOGMOTE) {
        notat.firstOrNull()?.todialogmeldingInnkallingDialogmote()
    } else {
        null
    },
    dialogmeldingForesporselOmPasier = if (dialogmeldingType == DialogmeldingType.DIALOGMELDING_SVAR_FORESPORSEL_OM_PASIENT) {
        notat.firstOrNull()?.toDialogmeldingForesporselOmPasient()
    } else {
        null
    },
    signaturDato = signaturDato,
    navnHelsepersonell = navnHelsePersonellNavn ?: ""

)

fun XMLNotat.toDialogmeldingHenvendelseFraLege(): DialogmeldingHenvendelseFraLege {

    return DialogmeldingHenvendelseFraLege(
        teamakode = temaKodet.toTeamakode(),
        tekstNotatInnhold = tekstNotatInnhold.toString()
    )
}

fun XMLNotat.todialogmeldingInnkallingDialogmote(): DialogmeldingInnkallingDialogmote {

    return DialogmeldingInnkallingDialogmote(
        teamakode = temaKodet.toTeamakode(),
        tekstNotatInnhold = tekstNotatInnhold.toString(),
        dokIdNotat = dokIdNotat,
        foresporsel = foresporsel.toForesporsel()
    )
}

fun XMLNotat.toDialogmeldingForesporselOmPasient(): DialogmeldingForesporselOmPasient {

    return DialogmeldingForesporselOmPasient(
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
        sporsmal = sporsmal,
        dokIdForesp = dokIdForesp,
        rollerRelatertNotat = RollerRelatertNotat(
            rolleNotat = RolleNotat(rollerRelatertNotat.first().rolleNotat.s, rollerRelatertNotat.first().rolleNotat.v),
            person = Person(rollerRelatertNotat.first().person.givenName, rollerRelatertNotat.first().person.familyName)
        )
    )
}
