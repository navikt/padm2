package no.nav.syfo.model

import no.nav.syfo.handlestatus.RULE_NAME_DUPLICATE

data class ValidationResult(
    val status: Status,
    val ruleHits: List<RuleInfo>
)

data class RuleInfo(
    val ruleName: String,
    val messageForSender: String,
    val messageForUser: String,
    val ruleStatus: Status
)

enum class Status {
    OK,
    INVALID
}

fun ValidationResult.isDuplicate() =
    (status == Status.INVALID) && ruleHits.any { ruleInfo -> ruleInfo.ruleName == RULE_NAME_DUPLICATE }
