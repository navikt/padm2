package no.nav.syfo.util

fun bearerHeader(token: String): String {
    return "Bearer $token"
}

const val NAV_CALL_ID_HEADER = "Nav-Call-Id"
