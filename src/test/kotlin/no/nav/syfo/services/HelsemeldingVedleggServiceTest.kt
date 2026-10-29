package no.nav.syfo.services

import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.helsemelding.AttachmentServiceClient
import no.nav.syfo.kafka.helsemelding.generateIncomingDialogMessageJson
import no.nav.syfo.kafka.helsemelding.receivedDialogmeldingFromHelsemelding
import no.nav.syfo.mock.MSG_ID_MED_VEDLEGG
import no.nav.syfo.mock.gyldigPdf
import no.nav.syfo.mock.pngBilde
import no.nav.syfo.model.ReceivedDialogmelding
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class HelsemeldingVedleggServiceTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val environment = externalMockEnvironment.environment

    private val vedleggService = HelsemeldingVedleggService(
        attachmentServiceClient = AttachmentServiceClient(
            azureAdV2Client = AzureAdV2Client(
                aadAppClient = environment.aadAppClient,
                aadAppSecret = environment.aadAppSecret,
                aadTokenEndpoint = environment.aadTokenEndpoint,
                httpClient = externalMockEnvironment.mockHttpClient,
            ),
            attachmentServiceClientId = environment.attachmentServiceClientId,
            attachmentServiceUrl = environment.attachmentServiceUrl,
            httpClient = externalMockEnvironment.mockHttpClient,
        ),
    )

    private fun dialogmelding(msgId: String, numberOfAttachments: Int): ReceivedDialogmelding =
        receivedDialogmeldingFromHelsemelding(
            dialogmeldingId = UUID.randomUUID().toString(),
            kafkaMelding = generateIncomingDialogMessageJson(id = msgId, numberOfAttachments = numberOfAttachments),
        )

    @Test
    fun `henter vedlegg og filtrerer bort ugyldige`() {
        val vedlegg = runBlocking {
            vedleggService.hentVedlegg(dialogmelding(msgId = MSG_ID_MED_VEDLEGG, numberOfAttachments = 3))
        }

        assertEquals(2, vedlegg.size)
        assertEquals("application/pdf", vedlegg[0].mimeType)
        assertEquals("Legeerklæring", vedlegg[0].beskrivelse)
        assertArrayEquals(gyldigPdf, vedlegg[0].contentBase64)
        assertEquals("image/png", vedlegg[1].mimeType)
        assertArrayEquals(pngBilde, vedlegg[1].contentBase64)
    }

    @Test
    fun `kaller ikke attachment-service naar meldingen ikke har vedlegg`() {
        // attachment-service-mocken svarer 404 for ukjent msgId, så et kall ville kastet.
        val vedlegg = runBlocking {
            vedleggService.hentVedlegg(dialogmelding(msgId = UUID.randomUUID().toString(), numberOfAttachments = 0))
        }

        assertTrue(vedlegg.isEmpty())
    }

    @Test
    fun `kaster naar antall vedlegg ikke stemmer med numberOfAttachments`() {
        assertThrows<IllegalStateException> {
            runBlocking {
                vedleggService.hentVedlegg(dialogmelding(msgId = MSG_ID_MED_VEDLEGG, numberOfAttachments = 4))
            }
        }
    }

    @Test
    fun `kaster naar vedlegg mangler i attachment-service`() {
        assertThrows<ClientRequestException> {
            runBlocking {
                vedleggService.hentVedlegg(dialogmelding(msgId = UUID.randomUUID().toString(), numberOfAttachments = 1))
            }
        }
    }
}
