package no.nav.syfo.client.wellknown

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.proxyConfig

fun getWellKnown(
    wellKnownUrl: String,
): WellKnown = runBlocking {
    HttpClient(Apache5, proxyConfig).use { client ->
        client.get(wellKnownUrl).body<WellKnownDTO>().toWellKnown()
    }
}
