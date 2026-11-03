package no.nav.syfo.client.wellknown

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.httpClientWithProxy

fun getWellKnown(
    wellKnownUrl: String,
): WellKnown = runBlocking {
    httpClientWithProxy.get(wellKnownUrl).body<WellKnownDTO>().toWellKnown()
}
