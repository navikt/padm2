package no.nav.syfo

import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.util.extractIdentFromBehandler
import no.nav.syfo.util.get
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.util.safeUnmarshal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ExtractFromFellesFormatTest {
    private val BEHANDLER_FNR = "01010112377"

    @Test
    internal fun `Extract behandlerident`() {
        val fellesformat = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
        )

        val behandlerIdent = extractIdentFromBehandler(fellesformat)

        assertEquals(BEHANDLER_FNR, behandlerIdent)
    }

    @Test
    internal fun `Extract behandlerident and return null if it doesn't exist`() {
        val fellesformat = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_dialog_svar_foresporsel_om_pasient.xml")
        )

        val behandlerIdent = extractIdentFromBehandler(fellesformat)

        assertNull(behandlerIdent)
    }

    @Test
    internal fun `Does not find behandlerident in RollerRelatertNotat`() {
        // We might want to support this at some point
        val fellesformat = safeUnmarshal(
            getFileAsString("src/test/resources/dialogmelding_kiropraktor.xml")
        )

        val behandlerIdent = extractIdentFromBehandler(fellesformat)

        assertNull(behandlerIdent)
    }

    @Test
    internal fun `Reads GenDate without timezone as timezone Oslo and converts to correct LocalDateTime`() {
        val localDateTime = LocalDateTime.parse("2019-01-16T21:57:36")
        val inputDate = "2019-01-16T21:57:36"
        val dialogNotat = generateDialogNotat(inputDate)

        val fellesformat = safeUnmarshal(dialogNotat)

        val msgHead: XMLMsgHead = fellesformat.get()
        val genDate = msgHead.msgInfo.genDate
        assertEquals(localDateTime, genDate)
    }

    @Test
    internal fun `Reads GenDate with timezone zulu and converts to correct LocalDateTime`() {
        val localDateTime = LocalDateTime.parse("2019-01-16T23:51:35.5317672")
        val inputDate = "2019-01-16T22:51:35.5317672z"
        val dialogNotat = generateDialogNotat(inputDate)

        val fellesformat = safeUnmarshal(dialogNotat)

        val msgHead: XMLMsgHead = fellesformat.get()
        val date = msgHead.msgInfo.genDate
        assertEquals(localDateTime, date)
    }

    @Test
    internal fun `Reads GenDate with timezone Oslo and converts to correct LocalDateTime`() {
        val localDateTime = LocalDateTime.parse("2019-01-16T22:51:35.5317672")
        val inputDate = "2019-01-16T22:51:35.5317672+01:00"
        val dialogNotat = generateDialogNotat(inputDate)

        val fellesformat = safeUnmarshal(dialogNotat)

        val msgHead: XMLMsgHead = fellesformat.get()
        val genDate = msgHead.msgInfo.genDate
        assertEquals(localDateTime, genDate)
    }

    @Test
    internal fun `Reads GenDate with timezone Madagascar and converts to correct summertime LocalDateTime`() {
        val localDateTime = LocalDateTime.parse("2020-09-30T18:00:31.4764563")
        val inputDate = "2020-09-30T19:00:31.4764563+03:00"
        val dialogNotat = generateDialogNotat(inputDate)

        val fellesformat = safeUnmarshal(dialogNotat)

        val msgHead: XMLMsgHead = fellesformat.get()
        val genDate = msgHead.msgInfo.genDate
        assertEquals(localDateTime, genDate)
    }
}

private fun generateDialogNotat(date: String): String {
    val dialogNotat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml")
    val startTag = "<GenDate>"
    val endTag = "</GenDate>"
    val start = dialogNotat.indexOf(startTag) + startTag.length
    val end = dialogNotat.indexOf(endTag)

    return dialogNotat.replaceRange(start, end, date)
}
