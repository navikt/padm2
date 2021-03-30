package no.nav.syfo.rules

import no.nav.syfo.client.HelsenettProxyBehandler
import no.nav.syfo.model.HelsepersonellKategori
import no.nav.syfo.model.Status

enum class HPRRuleChain(
    override val ruleId: Int?,
    override val status: Status,
    override val messageForUser: String,
    override val messageForSender: String,
    override val predicate: (RuleData<HelsenettProxyBehandler>) -> Boolean
) : Rule<RuleData<HelsenettProxyBehandler>> {

    BEHANDLER_IKKE_GYLDIG_I_HPR(
        1402,
        Status.INVALID,
        "Den som skrev dialogmeldingen manglet autorisasjon.",
        "Dialogmelding kan ikke rettes, det må skrives en ny. Grunnet følgende:" +
                "Behandler er ikke gyldig i HPR på konsultasjonstidspunkt",
        { (_, behandler) ->
            !behandler.godkjenninger.any {
                it.autorisasjon?.aktiv != null && it.autorisasjon.aktiv
            }
        }),

    BEHANDLER_MANGLER_AUTORISASJON_I_HPR(
        1403,
        Status.INVALID,
        "Den som skrev dialogmeldingen manglet autorisasjon.",
        "Behandler har ikke gyldig autorisasjon i HPR",
        { (_, behandler) ->
            !behandler.godkjenninger.any {
                it.autorisasjon?.aktiv != null &&
                        it.autorisasjon.aktiv &&
                        it.autorisasjon.oid == 7704 &&
                        it.autorisasjon.verdi != null &&
                        it.autorisasjon.verdi in arrayOf("1", "17", "4", "2", "14", "18")
            }
        }),

    BEHANDLER_IKKE_LE_KI_MT_TL_FT_PS_I_HPR(
        1407,
        Status.INVALID,
        "Den som skrev dialogmelding manglet autorisasjon.",
        "Dialogmelding kan ikke rettes, det må skrives en ny. Grunnet følgende:" +
                "Behandler finnes i HPR men er ikke lege, kiropraktor, fysioterapeut, manuellterapeut tannlege eller psykolog",
        { (_, behandler) ->
            !behandler.godkjenninger.any {
                it.helsepersonellkategori?.aktiv != null &&
                        it.autorisasjon?.aktiv == true && it.helsepersonellkategori.verdi != null &&
                        harAktivHelsepersonellAutorisasjonsSom(
                            behandler, listOf(
                                HelsepersonellKategori.LEGE.verdi,
                                HelsepersonellKategori.KIROPRAKTOR.verdi,
                                HelsepersonellKategori.MANUELLTERAPEUT.verdi,
                                HelsepersonellKategori.TANNLEGE.verdi,
                                HelsepersonellKategori.FYSIOTERAPAEUT.verdi,
                                HelsepersonellKategori.PSYKOLOG.verdi
                            )
                        )
            }
        }),
}

fun harAktivHelsepersonellAutorisasjonsSom(
    behandler: HelsenettProxyBehandler,
    helsepersonerVerdi: List<String>
): Boolean =
    behandler.godkjenninger.any { godkjenning ->
        godkjenning.helsepersonellkategori?.aktiv != null &&
                godkjenning.autorisasjon?.aktiv == true && godkjenning.helsepersonellkategori.verdi != null &&
                godkjenning.helsepersonellkategori.let {
                    it.aktiv && it.verdi in helsepersonerVerdi
                }
    }
