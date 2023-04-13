package no.nav.syfo.client

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.serialization.jackson.*
import no.nav.syfo.util.configure
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    install(ContentNegotiation) {
        jackson { configure() }
    }
    install(HttpRequestRetry) {
        retryOnExceptionIf(2) { _, cause ->
            cause !is ClientRequestException
        }
        constantDelay(500L)
    }
    expectSuccess = false
}

val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    install(ContentNegotiation) {
        jackson { configure() }
    }
    install(HttpRequestRetry) {
        retryOnExceptionIf(2) { _, cause ->
            cause !is ClientRequestException
        }
        constantDelay(500L)
    }
    expectSuccess = true
    engine {
        customizeClient {
            setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        }
    }
}

val httpClient = HttpClient(Apache, config)
val httpClientWithProxy = HttpClient(Apache, proxyConfig)
