package no.nav.syfo.rules

import io.mockk.mockk
import no.nav.syfo.model.Dialogmelding
import org.amshove.kluent.shouldEqual
import org.junit.Test

internal class LegesuspensjonRuleChainTest {

    val dialogmelding = mockk<Dialogmelding>()

    fun ruleData(
        dialogmelding: Dialogmelding,
        suspended: Boolean
    ): RuleData<Boolean> = RuleData(dialogmelding, suspended)

    @Test
    internal fun `Should check rule BEHANDLER_SUSPENDERT, should trigger rule`() {
        val suspended = true

        LegesuspensjonRuleChain.BEHANDLER_SUSPENDERT(ruleData(dialogmelding, suspended)) shouldEqual true
    }

    @Test
    internal fun `Should check rule BEHANDLER_SUSPENDERT, should NOT trigger rule`() {
        val suspended = false

        LegesuspensjonRuleChain.BEHANDLER_SUSPENDERT(ruleData(dialogmelding, suspended)) shouldEqual false
    }
}
