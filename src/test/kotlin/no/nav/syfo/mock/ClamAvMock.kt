package no.nav.syfo.mock

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.getRandomPort
import no.nav.syfo.util.configure

class ClamAvMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val clamAvResponseSuccessful = listOf(
        MyScanResult(
            Filename = "file1",
            Result = MyScanStatus.OK,
        ),
    )

    val clamAvResponseUnsuccessful = listOf(
        MyScanResult(
            Filename = "file1",
            Result = MyScanStatus.FOUND,
        ),
    )

    val name = "clamav"
    val server = mockClamAvServer(
        port
    )

    private fun mockClamAvServer(
        port: Int
    ): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            install(ContentNegotiation) {
                jackson { configure() }
            }
            routing {
                post("/scan") {
                    val content = mutableListOf<ContentDisposition?>()
                    call.receiveMultipart().forEachPart {
                        content.add(it.contentDisposition)
                    }
                    val firstDisposiotion = content[0]!!
                    call.respond(
                        if (firstDisposiotion.parameter("filename") == "problem file")
                            clamAvResponseUnsuccessful
                        else
                            clamAvResponseSuccessful
                    )
                }
            }
        }
    }

    data class MyScanResult(
        val Filename: String,
        val Result: MyScanStatus,
    )

    enum class MyScanStatus {
        FOUND, OK, ERROR
    }
}
