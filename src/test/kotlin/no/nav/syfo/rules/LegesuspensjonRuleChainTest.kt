package no.nav.syfo.rules

import io.mockk.mockk
import no.nav.syfo.model.Dialogmelding
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LegesuspensjonRuleChainTest {

    val dialogmelding = mockk<Dialogmelding>()

    fun ruleData(
        dialogmelding: Dialogmelding,
        suspended: Boolean
    ): RuleData<Boolean> = RuleData(dialogmelding, suspended)

    @Test
    internal fun `Should check rule BEHANDLER_SUSPENDERT, should trigger rule`() {
        val suspended = true

        assertTrue(LegesuspensjonRuleChain.BEHANDLER_SUSPENDERT(ruleData(dialogmelding, suspended)))
    }

    @Test
    internal fun `Should check rule BEHANDLER_SUSPENDERT, should NOT trigger rule`() {
        val suspended = false

        assertFalse(LegesuspensjonRuleChain.BEHANDLER_SUSPENDERT(ruleData(dialogmelding, suspended)))
    }
}
