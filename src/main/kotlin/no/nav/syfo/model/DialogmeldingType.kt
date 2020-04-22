package no.nav.syfo.model

enum class DialogmeldingType(val ebxmlService: String, val ebxmlAction: String, val dialogmeldingKodeverk: List<DialogmeldingKodeverk>, val brevkode: String) {
    DIALOGMELDING_SVAR_INNKALLING_DIALOGMOTE("DialogmoteInnkalling", "MoteRespons", listOf(
        DialogmeldingKodeverk.SVAR_PAA_INNKALLING_DIALOGMOTE_JA_JEG_KOMMER,
        DialogmeldingKodeverk.SVAR_PAA_INNKALLING_DIALOGMOTE_JEG_ONSKER_NY_TID,
        DialogmeldingKodeverk.SVAR_PAA_INNKALLING_DIALOGMOTE_NEI_JEG_KAN_IKKE_KOMME), "900005"),
    DIALOGMELDING_SVAR_FORESPORSEL_OM_PASIENT("ForesporselFraSaksbehandler", "ForesporselSvar", listOf(
        DialogmeldingKodeverk.FORESPORSEL_OM_PASIENT_FORESPORSEL_OM_PASIENT,
        DialogmeldingKodeverk.FORESPORSEL_OM_PASIENT_PAAMINNELSE_FORESPORSEL_OM_PASIENT,
        DialogmeldingKodeverk.OVERFORING_EPJ_INFORMASJON_SVAR_PAA_FORESPORSEL_OM_PASIENT), "900006"),
    DIALOGMELDING_HENVENDELSE_FRA_LEGE("HenvendelseFraLege", "Henvendelse", listOf(
        DialogmeldingKodeverk.HENVENDELSE_OM_PASIENT_HENVENDELSE_IKKE_SYKMELDT_PASIENT,
        DialogmeldingKodeverk.HENVENDELSE_OM_PASIENT_HENVENDELSE_OM_SYKEFRAVARSOPPFOLGING
    ), "900007")
}

fun findDialogmeldingType(ebxmlService: String, ebxmlAction: String): DialogmeldingType {
    return DialogmeldingType.values().first { it.ebxmlService == ebxmlService && it.ebxmlAction == ebxmlAction }
}
