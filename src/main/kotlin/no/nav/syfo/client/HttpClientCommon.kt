package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    install(JsonFeature) {
        serializer = JacksonSerializer {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    expectSuccess = false
}

val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    config()
    engine {
        customizeClient {
            setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        }
    }
}

val httpClient = HttpClient(Apache, config)
val httpClientWithProxy = HttpClient(Apache, proxyConfig)
