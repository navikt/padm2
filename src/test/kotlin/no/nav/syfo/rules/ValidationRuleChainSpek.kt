package no.nav.syfo.rules

import io.mockk.mockk
import java.time.LocalDateTime
import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.model.RuleMetadata
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

internal class ValidationRuleChainSpek {

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
        ValidationRuleChain.UGYLDIG_INNBYGGERIDENT_LENGDE(
            ruleData(dialogmelding, patientPersonNumber = "3006310441")
        ) shouldBeEqualTo true
    }

    @Test
    internal fun `Should check rule UGYLDIG_IDENT_LENGDE, should NOT trigger rule`() {
        ValidationRuleChain.UGYLDIG_INNBYGGERIDENT_LENGDE(
            ruleData(dialogmelding, patientPersonNumber = "04030350265")
        ) shouldBeEqualTo false
    }

    @Test
    internal fun `Should check rule UGYLDIG_IDENT_PASIENT, should trigger rule`() {
        ValidationRuleChain.UGYLDIG_INNBYGGERIDENT(
            ruleData(dialogmelding, patientPersonNumber = "30063104424")
        ) shouldBeEqualTo true
    }

    @Test
    internal fun `Should check rule UGYLDIG_IDENT_PASIENT, invalid DNR should trigger rule`() {
        ValidationRuleChain.UGYLDIG_INNBYGGERIDENT(
            ruleData(dialogmelding, patientPersonNumber = "70063104424")
        ) shouldBeEqualTo true
    }

    @Test
    internal fun `Should check rule UGYLDIG_IDENT, should NOT trigger rule`() {
        ValidationRuleChain.UGYLDIG_INNBYGGERIDENT(
            ruleData(dialogmelding, patientPersonNumber = "04030350265")
        ) shouldBeEqualTo false
    }

    @Test
    internal fun `Should check rule UGYLDIG_IDENT, valid DNR should NOT trigger rule`() {
        ValidationRuleChain.UGYLDIG_INNBYGGERIDENT(
            ruleData(dialogmelding, patientPersonNumber = "45088649080")
        ) shouldBeEqualTo false
    }

    @Test
    internal fun `UGYLDIG_FNR_AVSENDER should trigger on rule`() {
        ValidationRuleChain.UGYLDIG_FNR_AVSENDER(
            ruleData(dialogmelding, avsenderfnr = "30063104424")
        ) shouldBeEqualTo true
    }

    @Test
    internal fun `UGYLDIG_FNR_AVSENDER should not trigger on rule`() {
        ValidationRuleChain.UGYLDIG_FNR_AVSENDER(
            ruleData(dialogmelding, avsenderfnr = "04030350265")
        ) shouldBeEqualTo false
    }

    @Test
    internal fun `AVSENDER_FNR_ER_SAMME_SOM_PASIENT_FNR should trigger on rule`() {
        ValidationRuleChain.AVSENDER_FNR_ER_SAMME_SOM_INNBYGGER_FNR(
            ruleData(dialogmelding, avsenderfnr = "30063104424", patientPersonNumber = "30063104424")
        ) shouldBeEqualTo true
    }

    @Test
    internal fun `AVSENDER_FNR_ER_SAMME_SOM_PASIENT_FNR should not trigger on rule`() {

        ValidationRuleChain.AVSENDER_FNR_ER_SAMME_SOM_INNBYGGER_FNR(
            ruleData(dialogmelding, avsenderfnr = "04030350265", patientPersonNumber = "04030350261")
        ) shouldBeEqualTo false
    }
}
