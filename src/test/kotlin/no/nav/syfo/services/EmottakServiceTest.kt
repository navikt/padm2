package no.nav.syfo.services

import io.mockk.clearMocks
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.SmgcpClient
import no.nav.syfo.client.TssId
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.get
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.util.safeUnmarshal
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EmottakServiceTest {

    private val smgcpClient = mockk<SmgcpClient>(relaxed = true)
    private val emottakService = EmottakService(smgcpClient = smgcpClient)

    private val felleformat = safeUnmarshal(
        getFileAsString("src/test/resources/dialogmelding_dialog_svar_foresporsel_om_pasient.xml")
    )

    private val msgHead: XMLMsgHead = felleformat.get()
    private val emottakBlokk = felleformat.get<XMLMottakenhetBlokk>()
    private val msgId = msgHead.msgInfo.msgId

    private val loggingMeta = LoggingMeta(
        mottakId = emottakBlokk.ediLoggId,
        orgNr = null,
        msgId = msgId,
        dialogmeldingId = msgId,
    )

    @BeforeEach
    fun beforeEach() {
        clearMocks(smgcpClient)
    }

    @Test
    fun `update emottak subscription`() {
        val tssId = TssId("123")
        val partnerReferanse = 12049
        val sender = msgHead.msgInfo.sender
        coJustRun { smgcpClient.startSubscription(any(), any(), any(), any()) }

        runBlocking {
            emottakService.registerEmottakSubscription(
                tssId = tssId,
                partnerReferanse = emottakBlokk.partnerReferanse,
                sender = msgHead.msgInfo.sender,
                msgId = msgId,
                loggingMeta = loggingMeta,
            )
        }

        coVerify(exactly = 1) { smgcpClient.startSubscription(tssId, sender, partnerReferanse, msgId) }
    }

    @Test
    fun `don't update emottak subscription if empty partnerref`() {
        val tssId = TssId("123")
        val emptyPartnerReferanse = ""
        coJustRun { smgcpClient.startSubscription(any(), any(), any(), any()) }

        runBlocking {
            emottakService.registerEmottakSubscription(
                tssId = tssId,
                partnerReferanse = emptyPartnerReferanse,
                sender = msgHead.msgInfo.sender,
                msgId = msgId,
                loggingMeta = loggingMeta,
            )
        }

        coVerify(exactly = 0) { smgcpClient.startSubscription(any(), any(), any(), any()) }
    }
}
