package no.nav.syfo.model

import no.nav.syfo.logger

enum class DialogmeldingType(val ebxmlService: String, val ebxmlAction: String, val dialogmeldingKodeverk: List<DialogmeldingKodeverk>, val brevkode: String) {
    DIALOGMELDING_DIALOGMOTE_INNKALLING_MOTERESPONS(
        "DialogmoteInnkalling", "MoteRespons",
        listOf(
            DialogmeldingKodeverk.SVAR_PAA_INNKALLING_DIALOGMOTE_JA_JEG_KOMMER,
            DialogmeldingKodeverk.SVAR_PAA_INNKALLING_DIALOGMOTE_JEG_ONSKER_NY_TID,
            DialogmeldingKodeverk.SVAR_PAA_INNKALLING_DIALOGMOTE_NEI_JEG_KAN_IKKE_KOMME
        ),
        "900005"
    ),
    DIALOGMELDING_FORESPORSEL_FRA_SAKSBEHANDLER_FORESPORSEL_SVAR(
        "ForesporselFraSaksbehandler", "ForesporselSvar",
        listOf(
            DialogmeldingKodeverk.FORESPORSEL_OM_PASIENT_FORESPORSEL_OM_PASIENT,
            DialogmeldingKodeverk.FORESPORSEL_OM_PASIENT_PAAMINNELSE_FORESPORSEL_OM_PASIENT,
            DialogmeldingKodeverk.OVERFORING_EPJ_INFORMASJON_SVAR_PAA_FORESPORSEL_OM_PASIENT
        ),
        "900006"
    ),
    DIALOGMELDING_HENVENDELSE_FRA_LEGE_HENDVENDELSE(
        "HenvendelseFraLege", "Henvendelse",
        listOf(
            DialogmeldingKodeverk.HENVENDELSE_OM_PASIENT_HENVENDELSE_IKKE_SYKMELDT_PASIENT,
            DialogmeldingKodeverk.HENVENDELSE_OM_PASIENT_HENVENDELSE_OM_SYKEFRAVARSOPPFOLGING
        ),
        "900007"
    )
}

fun findDialogmeldingType(ebxmlService: String, ebxmlAction: String): DialogmeldingType {
    return DialogmeldingType.values().first { it.ebxmlService == ebxmlService && it.ebxmlAction == ebxmlAction }
}

fun findDialogmeldingKodeverk(kodeverkOID: String, v: String): DialogmeldingKodeverk? {
    val kodeverk = DialogmeldingKodeverk.values().firstOrNull { it.kodeverkOID == kodeverkOID && it.v == v }

    if (kodeverk == null) {
        logger.warn("Fant ikke kodeverk som matcher dialogmeldingen. kodeverkOID: $kodeverkOID, v: $v")
    }

    return kodeverk
}

fun DialogmeldingType.isHenvendelseFraLegeOrForesporselSvar(): Boolean =
    this == DialogmeldingType.DIALOGMELDING_HENVENDELSE_FRA_LEGE_HENDVENDELSE ||
        this == DialogmeldingType.DIALOGMELDING_FORESPORSEL_FRA_SAKSBEHANDLER_FORESPORSEL_SVAR
