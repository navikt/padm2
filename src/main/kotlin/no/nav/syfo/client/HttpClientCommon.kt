package no.nav.syfo.client


import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import no.nav.syfo.util.configureJacksonMapper
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    install(JsonFeature) {
        serializer = JacksonSerializer(configuredJacksonMapper())
    }
    expectSuccess = false
}

val httpClientWithProxyAndTimeout: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    install(JsonFeature) {
        serializer = JacksonSerializer(configuredJacksonMapper())
    }
    install(HttpTimeout) {
        socketTimeoutMillis = 60000
    }
    engine {
        customizeClient {
            setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        }
    }
}

val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    install(JsonFeature) {
        serializer = JacksonSerializer(configuredJacksonMapper())
    }
    engine {
        customizeClient {
            setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        }
    }
}

val httpClient = HttpClient(Apache, config)
val httpClientWithTimeout = HttpClient(Apache, httpClientWithProxyAndTimeout)
val httpClientWithProxy = HttpClient(Apache, proxyConfig)

fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        jackson(block = configureJacksonMapper())
    }
}
