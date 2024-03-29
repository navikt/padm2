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

fun XMLNotat.toHenvendelseFraLegeHenvendelse(): HenvendelseFraLegeHenvendelse? {
    return temaKodet.toTemaKode()?.let { it ->
        HenvendelseFraLegeHenvendelse(
            temaKode = it,
            tekstNotatInnhold = tekstNotatInnhold?.toString() ?: "",
            dokIdNotat = dokIdNotat,
            foresporsel = null, // Ignore because EPJ send incorrect data and we don't use it
            rollerRelatertNotat = if (rollerRelatertNotat.isNotEmpty()) {
                RollerRelatertNotat(
                    rolleNotat = if (rollerRelatertNotat.firstOrNull()?.person != null) {
                        RolleNotat(
                            rollerRelatertNotat.first().rolleNotat.s,
                            rollerRelatertNotat.first().rolleNotat.v
                        )
                    } else {
                        null
                    },
                    person = if (rollerRelatertNotat.firstOrNull()?.person != null) {
                        Person(
                            rollerRelatertNotat.first().person.givenName ?: "",
                            rollerRelatertNotat.first().person.familyName ?: "",
                        )
                    } else {
                        null
                    },
                    helsepersonell = if (rollerRelatertNotat.firstOrNull()?.healthcareProfessional != null) {
                        Helsepersonell(
                            rollerRelatertNotat.first().healthcareProfessional.givenName ?: "",
                            rollerRelatertNotat.first().healthcareProfessional.familyName ?: "",
                        )
                    } else {
                        null
                    }
                )
            } else {
                null
            }
        )
    }
}

fun CV.toTemaKode(): TemaKode? {
    return findDialogmeldingKodeverk(s, v)?.toTypeMelding()
}

fun DialogmeldingKodeverk.toTypeMelding(): TemaKode {
    return TemaKode(kodeverkOID, dn, v, arenaNotatKategori, arenaNotatKode, arenaNotatTittel)
}

fun XMLNotat.toInnkallingMoterespons(): InnkallingMoterespons? {
    return temaKodet.toTemaKode()?.let { it ->
        InnkallingMoterespons(
            temaKode = it,
            tekstNotatInnhold = tekstNotatInnhold?.toString(),
            dokIdNotat = dokIdNotat,
            foresporsel = foresporsel?.toForesporsel()
        )
    }
}

fun XMLNotat.toForesporselFraSaksbehandlerForesporselSvar(): ForesporselFraSaksbehandlerForesporselSvar? {
    return temaKodet.toTemaKode()?.let { it ->
        ForesporselFraSaksbehandlerForesporselSvar(
            temaKode = it,
            tekstNotatInnhold = tekstNotatInnhold?.toString() ?: "",
            dokIdNotat = dokIdNotat,
            datoNotat = datoNotat?.toGregorianCalendar()?.toZonedDateTime()?.toLocalDateTime()
        )
    }
}

fun XMLForesporsel.toForesporsel(): Foresporsel {
    val description = typeForesp.dn ?: ""

    return Foresporsel(
        typeForesp = TypeForesp(description, typeForesp.s, typeForesp.v),
        sporsmal = sporsmal,
        dokIdForesp = dokIdForesp,
        rollerRelatertNotat = if (rollerRelatertNotat.isNotEmpty()) {
            RollerRelatertNotat(
                rolleNotat = if (rollerRelatertNotat.firstOrNull()?.person != null) {
                    RolleNotat(
                        rollerRelatertNotat.first().rolleNotat.s,
                        rollerRelatertNotat.first().rolleNotat.v
                    )
                } else {
                    null
                },
                person = if (rollerRelatertNotat.firstOrNull()?.person != null) {
                    Person(rollerRelatertNotat.first().person.givenName, rollerRelatertNotat.first().person.familyName)
                } else {
                    null
                },
                helsepersonell = if (rollerRelatertNotat.firstOrNull()?.healthcareProfessional != null) {
                    Helsepersonell(
                        rollerRelatertNotat.first().healthcareProfessional.givenName,
                        rollerRelatertNotat.first().healthcareProfessional.familyName
                    )
                } else {
                    null
                }
            )
        } else {
            null
        }
    )
}
