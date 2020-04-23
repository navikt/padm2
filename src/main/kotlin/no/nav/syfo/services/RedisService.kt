package no.nav.syfo.services

import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.syfo.objectMapper
import redis.clients.jedis.Jedis

fun updateRedis(jedis: Jedis, ediLoggId: String, sha256String: String) {
    jedis.setex(ediLoggId, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
    jedis.setex(sha256String, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
}

fun sha256hashstring(dialogmelding: XMLDialogmelding): String =
        MessageDigest.getInstance("SHA-256")
                .digest(objectMapper.writeValueAsBytes(dialogmelding))
                .fold("") { str, it -> str + "%02x".format(it) }
