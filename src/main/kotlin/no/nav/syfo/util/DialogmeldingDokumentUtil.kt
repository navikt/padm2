package no.nav.syfo.util

import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.syfo.objectMapper
import java.security.MessageDigest

fun sha256hashstring(dialogmelding: XMLDialogmelding): String =
    MessageDigest.getInstance("SHA-256")
        .digest(objectMapper.writeValueAsBytes(dialogmelding))
        .fold("") { str, it -> str + "%02x".format(it) }
