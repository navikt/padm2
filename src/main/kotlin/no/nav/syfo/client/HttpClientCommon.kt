package no.nav.syfo.client

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.serialization.jackson.*
import no.nav.syfo.util.configure
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

val commonConfig: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit = {
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
}

val pdfGenConfig: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit = {
    this.commonConfig()
    install(HttpTimeout) {
        requestTimeoutMillis = 120 * 1000
    }
}

val retryAllConfig: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit = {
    install(ContentNegotiation) {
        jackson { configure() }
    }
    install(HttpRequestRetry) {
        retryOnExceptionIf(2) { _, cause ->
            cause is ServerResponseException || cause is ClientRequestException
        }
        constantDelay(500L)
    }
    expectSuccess = true
}

val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    this.commonConfig()
    engine {
        customizeClient {
            setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        }
    }
}

val httpClient = HttpClient(Apache, commonConfig)
val httpClientRetryAll = HttpClient(Apache, retryAllConfig)
val httpClientWithProxy = HttpClient(Apache, proxyConfig)
val httpClientPdfgen = HttpClient(Apache, pdfGenConfig)
