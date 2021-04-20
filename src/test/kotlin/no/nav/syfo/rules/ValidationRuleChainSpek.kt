package no.nav.syfo.rules

import io.mockk.mockk
import java.time.LocalDateTime
import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.model.RuleMetadata
import org.amshove.kluent.shouldEqual
import org.junit.Test

internal class ValidationRuleChainSpek {

    val dialogmelding = mockk<Dialogmelding>()

    fun ruleData(
        dialogmelding: Dialogmelding = mockk<Dialogmelding>(),
        receivedDate: LocalDateTime = LocalDateTime.now(),
        signatureDate: LocalDateTime = LocalDateTime.now(),
        patientPersonNumber: String = "1234567891",
        legekontorOrgNr: String = "123456789",
        tssid: String? = "1314445",
        avsenderfnr: String = "131515"
    ): RuleData<RuleMetadata> = RuleData(
        dialogmelding,
        RuleMetadata(signatureDate, receivedDate, patientPersonNumber, legekontorOrgNr, tssid, avsenderfnr)
    )

    @Test
    internal fun `Should check rule UGYLDIG_FNR_LENGDE, should trigger rule`() {
        ValidationRuleChain.UGYLDIG_FNR_LENGDE_PASIENT(
            ruleData(dialogmelding, patientPersonNumber = "3006310441")
        ) shouldEqual true
    }

    @Test
    internal fun `Should check rule UGYLDIG_FNR_LENGDE, should NOT trigger rule`() {
        ValidationRuleChain.UGYLDIG_FNR_LENGDE_PASIENT(
            ruleData(dialogmelding, patientPersonNumber = "04030350265")
        ) shouldEqual false
    }

    @Test
    internal fun `Should check rule UGYLDIG_FNR_PASIENT, should trigger rule`() {
        ValidationRuleChain.UGYLDIG_FNR_PASIENT(
            ruleData(dialogmelding, patientPersonNumber = "30063104424")
        ) shouldEqual true
    }

    @Test
    internal fun `Should check rule UGYLDIG_FNR, should NOT trigger rule`() {
        ValidationRuleChain.UGYLDIG_FNR_PASIENT(
            ruleData(dialogmelding, patientPersonNumber = "04030350265")
        ) shouldEqual false
    }

    @Test
    internal fun `UGYLDIG_FNR_AVSENDER should trigger on rule`() {
        ValidationRuleChain.UGYLDIG_FNR_AVSENDER(
            ruleData(dialogmelding, avsenderfnr = "30063104424")
        ) shouldEqual true
    }

    @Test
    internal fun `UGYLDIG_FNR_AVSENDER should not trigger on rule`() {
        ValidationRuleChain.UGYLDIG_FNR_AVSENDER(
            ruleData(dialogmelding, avsenderfnr = "04030350265")
        ) shouldEqual false
    }

    @Test
    internal fun `AVSENDER_FNR_ER_SAMME_SOM_PASIENT_FNR should trigger on rule`() {
        ValidationRuleChain.AVSENDER_FNR_ER_SAMME_SOM_PASIENT_FNR(
            ruleData(dialogmelding, avsenderfnr = "30063104424", patientPersonNumber = "30063104424")
        ) shouldEqual true
    }

    @Test
    internal fun `AVSENDER_FNR_ER_SAMME_SOM_PASIENT_FNR should not trigger on rule`() {

        ValidationRuleChain.AVSENDER_FNR_ER_SAMME_SOM_PASIENT_FNR(
            ruleData(dialogmelding, avsenderfnr = "04030350265", patientPersonNumber = "04030350261")
        ) shouldEqual false
    }
}
