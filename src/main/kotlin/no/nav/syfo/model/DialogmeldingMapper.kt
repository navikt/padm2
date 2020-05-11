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
    henvendelseFraLegeHenvendelse = if (dialogmeldingType == DialogmeldingType.DIALOGMELDING_HENVENDELSE_FRA_LEGE_HENDVENDELSE) {
        notat.firstOrNull()?.toHenvendelseFraLegeHenvendelse()
    } else {
        null
    },
    innkallingMoterespons = if (dialogmeldingType == DialogmeldingType.DIALOGMELDING_DIALOGMOTE_INNKALLING_MOTERESPONS) {
        notat.firstOrNull()?.toInnkallingMoterespons()
    } else {
        null
    },
    foresporselFraSaksbehandlerForesporselSvar = if (dialogmeldingType == DialogmeldingType.DIALOGMELDING_FORESPORSEL_FRA_SAKSBEHANDLER_FORESPORSEL_SVAR) {
        notat.firstOrNull()?.toForesporselFraSaksbehandlerForesporselSvar()
    } else {
        null
    },
    signaturDato = signaturDato,
    navnHelsepersonell = navnHelsePersonellNavn ?: ""

)

fun XMLNotat.toHenvendelseFraLegeHenvendelse(): HenvendelseFraLegeHenvendelse {

    return HenvendelseFraLegeHenvendelse(
        teamakode = temaKodet.toTeamakode(),
        tekstNotatInnhold = tekstNotatInnhold.toString(),
        dokIdNotat = dokIdNotat,
        foresporsel = foresporsel?.toForesporsel()
    )
}

fun CV.toTeamakode(): TemaKode {
    return findDialogmeldingKodeverk(s, v).toTypeMelding()
}

fun DialogmeldingKodeverk.toTypeMelding(): TemaKode {
    return TemaKode(kodeverkOID, dn, v, arenaNotatKategori, arenaNotatKode, arenaNotatTittel)
}

fun XMLNotat.toInnkallingMoterespons(): InnkallingMoterespons {

    return InnkallingMoterespons(
        teamakode = temaKodet.toTeamakode(),
        tekstNotatInnhold = tekstNotatInnhold?.toString(),
        dokIdNotat = dokIdNotat,
        foresporsel = foresporsel?.toForesporsel()
    )
}

fun XMLNotat.toForesporselFraSaksbehandlerForesporselSvar(): ForesporselFraSaksbehandlerForesporselSvar {

    return ForesporselFraSaksbehandlerForesporselSvar(
        teamakode = temaKodet.toTeamakode(),
        tekstNotatInnhold = tekstNotatInnhold.toString(),
        dokIdNotat = dokIdNotat,
        datoNotat = datoNotat?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDateTime()
    )
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
