package no.nav.syfo.model

enum class DialogmeldingKodeverk(val kodeverkOID: String, val dn: String, val v: String, val arenaNotatKategori: String, val arenaNotatKode: String, val arenaNotatTittel: String) {
    PRODUKSJON_AV_NOTAT_FORFATTER("2.16.578.1.12.4.1.1.9057", "Forfatter", "1", "", "", ""),
    SVAR_PAA_INNKALLING_DIALOGMOTE_JA_JEG_KOMMER("2.16.578.1.12.4.1.1.8126", "Ja, jeg kommer", "1", "1", "11", "Svar innkalling dialogmøte:Ja, jeg kommer"),
    SVAR_PAA_INNKALLING_DIALOGMOTE_JEG_ONSKER_NY_TID("2.16.578.1.12.4.1.1.8126", "Jeg ønsker nytt møtetidspunkt", "2", "1", "12", "Svar innkalling dialogmøte:Jeg ønsker nytt møtetidspunkt"),
    SVAR_PAA_INNKALLING_DIALOGMOTE_NEI_JEG_KAN_IKKE_KOMME("2.16.578.1.12.4.1.1.8126", "Jeg kan ikke komme / begrunnelse for manglende oppmøte", "3", "1", "13", "Svar innkalling dialogmøte:Jeg kan ikke komme, se begrunnelse"),
    FORESPORSEL_OM_PASIENT_FORESPORSEL_OM_PASIENT("2.16.578.1.12.4.1.1.8129", "Forespørsel om pasient", "1", "", "", ""),
    FORESPORSEL_OM_PASIENT_PAAMINNELSE_FORESPORSEL_OM_PASIENT("2.16.578.1.12.4.1.1.8129", "Påminnelse forespørsel om pasient", "2", "", "", ""),
    OVERFORING_EPJ_INFORMASJON_SVAR_PAA_FORESPORSEL_OM_PASIENT("2.16.578.1.12.4.1.1.9069", "Svar på forespørsel", "5", "2", "25", "Svar på forespørsel om pasient"),
    HENVENDELSE_OM_PASIENT_HENVENDELSE_OM_SYKEFRAVARSOPPFOLGING("2.16.578.1.12.4.1.1.8128", "Henvendelse om sykefraværsoppfølging", "1", "3", "31", "Henvendelse fra lege"),
    HENVENDELSE_OM_PASIENT_HENVENDELSE_IKKE_SYKMELDT_PASIENT("2.16.578.1.12.4.1.1.8128", "Henvendelse om pasient som ikke er sykmeldt", "2", "3", "32", "Henvendelse fra lege")

    // The kodeverk can be find here:
    // 2.16.578.1.12.4.1.1.8129: https://volven.no/produkt.asp?id=361531&catID=3&subID=8
    // 2.16.578.1.12.4.1.1.8126: https://volven.no/produkt.asp?id=361528&catID=3&subID=8
    // 2.16.578.1.12.4.1.1.9057: https://volven.no/produkt.asp?id=361649&catID=3&subID=8
    // 2.16.578.1.12.4.1.1.9069: https://volven.no/produkt.asp?id=360703&catID=3&subID=8
    // 2.16.578.1.12.4.1.1.8128: https://volven.no/produkt.asp?id=361530&catID=3&subID=8
}
