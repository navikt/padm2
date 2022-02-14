package no.nav.syfo.util

import org.junit.Test
import java.io.IOException
import org.amshove.kluent.shouldBeEqualTo

internal class NetworkHelpersTest {
    @Test
    internal fun `Should find a IOException in a nested Exception`() {
        isCausedBy(Exception(IOException("Connection timed out")), 3, arrayOf(IOException::class)) shouldBeEqualTo true
    }

    @Test
    internal fun `Should find not a IOException in a nested Exception`() {
        isCausedBy(
            Exception(IOException("Connection timed out")),
            3,
            arrayOf(RuntimeException::class)
        ) shouldBeEqualTo false
    }

    @Test
    internal fun `Should not find a IOException whenever the cause stack is too deep`() {
        isCausedBy(
            Exception(Exception(Exception(IOException("Connection timed out")))),
            3,
            arrayOf(IOException::class)
        ) shouldBeEqualTo false
    }

    @Test
    internal fun `Should find a IOException whenever the cause stack is 3 deep`() {
        isCausedBy(
            Exception(Exception(IOException("Connection timed out"))),
            3,
            arrayOf(IOException::class)
        ) shouldBeEqualTo true
    }
}
