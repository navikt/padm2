package no.nav.syfo.model

/**
 * Kodeverk 8279 «Meldingens funksjon» (MsgHead/MsgInfo/Type).
 */
enum class DialogmeldingtypeKodeverk(val visningsnavn: String) {
    DIALOG_FORESPORSEL("Forespørsel"),
    DIALOG_SVAR("Svar på forespørsel"),
    DIALOG_NOTAT("Notat");

    companion object {
        fun visningsnavn(msgType: String): String =
            values().find { it.name == msgType }?.visningsnavn ?: msgType
    }
}
