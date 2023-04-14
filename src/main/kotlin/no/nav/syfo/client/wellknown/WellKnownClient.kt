package no.nav.syfo.client.wellknown

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.proxyConfig

fun getWellKnown(
    wellKnownUrl: String,
): WellKnown = runBlocking {
    HttpClient(Apache, proxyConfig).use { client ->
        client.get(wellKnownUrl).body<WellKnownDTO>().toWellKnown()
    }
}
