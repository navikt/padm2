package no.nav.syfo.client

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.features.*
import io.ktor.jackson.*
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
val httpClientWithProxy = HttpClient(Apache, proxyConfig)

fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        jackson(block = configureJacksonMapper())
    }
}
