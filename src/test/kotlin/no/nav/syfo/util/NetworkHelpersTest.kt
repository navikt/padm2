package no.nav.syfo.util

import org.amshove.kluent.shouldEqual
import org.junit.Test
import java.io.IOException

internal class NetworkHelpersTest {
    @Test
    internal fun `Should find a IOException in a nested Exception`() {
        isCausedBy(Exception(IOException("Connection timed out")), 3, arrayOf(IOException::class)) shouldEqual true
    }

    @Test
    internal fun `Should find not a IOException in a nested Exception`() {
        isCausedBy(
            Exception(IOException("Connection timed out")),
            3,
            arrayOf(RuntimeException::class)
        ) shouldEqual false
    }

    @Test
    internal fun `Should not find a IOException whenever the cause stack is too deep`() {
        isCausedBy(
            Exception(Exception(Exception(IOException("Connection timed out")))),
            3,
            arrayOf(IOException::class)
        ) shouldEqual false
    }

    @Test
    internal fun `Should find a IOException whenever the cause stack is 3 deep`() {
        isCausedBy(
            Exception(Exception(IOException("Connection timed out"))),
            3,
            arrayOf(IOException::class)
        ) shouldEqual true
    }
}
