package no.nav.syfo

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.util.configure
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class SelfTest {
    fun ApplicationTestBuilder.setupApiAndClient(
        applicationState: ApplicationState,
    ): HttpClient {
        application {
            routing {
                registerNaisApi(applicationState)
            }
        }
        val client = createClient {
            install(ContentNegotiation) {
                jackson { configure() }
            }
        }
        return client
    }

    @Test
    fun `Returns ok on is_alive`() {
        testApplication {
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            val client = setupApiAndClient(applicationState)
            val response = client.get("/is_alive") {}
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("I'm alive! :)", response.body<String>())
        }
    }

    @Test
    fun `Returns ok on is_ready`() {
        testApplication {
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            val client = setupApiAndClient(applicationState)
            val response = client.get("/is_ready") {}
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("I'm ready! :)", response.body<String>())
        }
    }

    @Test
    fun `Returns internal server error when liveness check fails`() {
        testApplication {
            val applicationState = ApplicationState()
            applicationState.ready = false
            applicationState.alive = false
            val client = setupApiAndClient(applicationState)
            val response = client.get("/is_alive") {}
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("I'm dead x_x", response.body<String>())
        }
    }

    @Test
    fun `Returns internal server error when readyness check fails`() {
        testApplication {
            val applicationState = ApplicationState()
            applicationState.ready = false
            applicationState.alive = false
            val client = setupApiAndClient(applicationState)
            val response = client.get("/is_ready") {}
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("Please wait! I'm not ready :(", response.body<String>())
        }
    }
}
