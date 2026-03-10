package no.nav.syfo

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.wellknown.WellKnown
import no.nav.syfo.mock.PdfGenMockState
import no.nav.syfo.mock.PdlMockState
import no.nav.syfo.mock.mockHttpClient
import java.nio.file.Paths

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()
    val wellKnownInternalAzureAD = wellKnownInternalAzureAD()

    val pdfgenMock = PdfGenMockState()
    val pdlMock = PdlMockState()

    val environment = testEnvironment()
    val mockHttpClient = mockHttpClient(
        environment = environment,
        pdfGenMockState = pdfgenMock,
        pdlMockState = pdlMock,
    )

    companion object {
        val instance: ExternalMockEnvironment by lazy {
            ExternalMockEnvironment().also {
                it.database.start()
            }
        }
    }
}

fun wellKnownInternalAzureAD(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        issuer = "https://sts.issuer.net/veileder/v2",
        jwksUri = uri.toString()
    )
}
