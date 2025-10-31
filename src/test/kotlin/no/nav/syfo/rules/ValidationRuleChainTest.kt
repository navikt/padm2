package no.nav.syfo.rules

import io.mockk.mockk
import java.time.LocalDateTime
import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.model.RuleMetadata
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ValidationRuleChainTest {

    val dialogmelding = mockk<Dialogmelding>()

    fun ruleData(
        dialogmelding: Dialogmelding = mockk<Dialogmelding>(),
        receivedDate: LocalDateTime = LocalDateTime.now(),
        signatureDate: LocalDateTime = LocalDateTime.now(),
        patientPersonNumber: String = "1234567891",
        legekontorOrgNr: String = "123456789",
        avsenderfnr: String = "131515"
    ): RuleData<RuleMetadata> = RuleData(
        dialogmelding,
        RuleMetadata(signatureDate, receivedDate, patientPersonNumber, legekontorOrgNr, avsenderfnr)
    )

    @Test
    internal fun `Should check rule UGYLDIG_IDENT_LENGDE, should trigger rule`() {
        assertTrue(
            ValidationRuleChain.UGYLDIG_INNBYGGERIDENT_LENGDE(
                ruleData(dialogmelding, patientPersonNumber = "3006310441")
            )
        )
    }

    @Test
    internal fun `Should check rule UGYLDIG_IDENT_LENGDE, should NOT trigger rule`() {
        assertFalse(
            ValidationRuleChain.UGYLDIG_INNBYGGERIDENT_LENGDE(
                ruleData(dialogmelding, patientPersonNumber = "04030350265")
            )
        )
    }

    @Test
    internal fun `Should check rule UGYLDIG_IDENT_PASIENT, should trigger rule`() {
        assertTrue(
            ValidationRuleChain.UGYLDIG_INNBYGGERIDENT(
                ruleData(dialogmelding, patientPersonNumber = "30063104424")
            )
        )
    }

    @Test
    internal fun `Should check rule UGYLDIG_IDENT_PASIENT, invalid DNR should trigger rule`() {
        assertTrue(
            ValidationRuleChain.UGYLDIG_INNBYGGERIDENT(
                ruleData(dialogmelding, patientPersonNumber = "70063104424")
            )
        )
    }

    @Test
    internal fun `Should check rule UGYLDIG_IDENT, should NOT trigger rule`() {
        assertFalse(
            ValidationRuleChain.UGYLDIG_INNBYGGERIDENT(
                ruleData(dialogmelding, patientPersonNumber = "04030350265")
            )
        )
    }

    @Test
    internal fun `Should check rule UGYLDIG_IDENT, valid DNR should NOT trigger rule`() {
        assertFalse(
            ValidationRuleChain.UGYLDIG_INNBYGGERIDENT(
                ruleData(dialogmelding, patientPersonNumber = "45088649080")
            )
        )
    }

    @Test
    internal fun `UGYLDIG_FNR_AVSENDER should trigger on rule`() {
        assertTrue(
            ValidationRuleChain.UGYLDIG_FNR_AVSENDER(
                ruleData(dialogmelding, avsenderfnr = "30063104424")
            )
        )
    }

    @Test
    internal fun `UGYLDIG_FNR_AVSENDER should not trigger on rule`() {
        assertFalse(
            ValidationRuleChain.UGYLDIG_FNR_AVSENDER(
                ruleData(dialogmelding, avsenderfnr = "04030350265")
            )
        )
    }

    @Test
    internal fun `AVSENDER_FNR_ER_SAMME_SOM_PASIENT_FNR should trigger on rule`() {
        assertTrue(
            ValidationRuleChain.AVSENDER_FNR_ER_SAMME_SOM_INNBYGGER_FNR(
                ruleData(dialogmelding, avsenderfnr = "30063104424", patientPersonNumber = "30063104424")
            )
        )
    }

    @Test
    internal fun `AVSENDER_FNR_ER_SAMME_SOM_PASIENT_FNR should not trigger on rule`() {
        assertFalse(
            ValidationRuleChain.AVSENDER_FNR_ER_SAMME_SOM_INNBYGGER_FNR(
                ruleData(dialogmelding, avsenderfnr = "04030350265", patientPersonNumber = "04030350261")
            )
        )
    }
}
