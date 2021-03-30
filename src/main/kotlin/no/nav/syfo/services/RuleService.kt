package no.nav.syfo.services

import io.ktor.util.KtorExperimentalAPI
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.GlobalScope
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.LegeSuspensjonClient
import no.nav.syfo.client.SyfohelsenettproxyClient
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.rules.HPRRuleChain
import no.nav.syfo.rules.LegesuspensjonRuleChain
import no.nav.syfo.rules.Rule
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.rules.executeFlow
import no.nav.syfo.util.LoggingMeta
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@KtorExperimentalAPI
class RuleService(
    val legeSuspensjonClient: LegeSuspensjonClient,
    val syfohelsenettproxyClient: SyfohelsenettproxyClient
) {

    private val log: Logger = LoggerFactory.getLogger("ruleservice")
    suspend fun executeRuleChains(receivedDialogmelding: ReceivedDialogmelding): ValidationResult =
        with(GlobalScope) {

            val loggingMeta = LoggingMeta(
                mottakId = receivedDialogmelding.navLogId,
                orgNr = receivedDialogmelding.legekontorOrgNr,
                msgId = receivedDialogmelding.msgId,
                dialogmeldingId = receivedDialogmelding.dialogmelding.id
            )

            log.info("Received a dialogmelding, going to rules, {}", fields(loggingMeta))

            val dialogmelding = receivedDialogmelding.dialogmelding

            val doctorSuspend = legeSuspensjonClient.checkTherapist(
                receivedDialogmelding.personNrLege,
                receivedDialogmelding.msgId,
                DateTimeFormatter.ISO_DATE.format(receivedDialogmelding.mottattDato)
            ).suspendert

            val avsenderBehandler = syfohelsenettproxyClient.finnBehandler(
                behandlerFnr = receivedDialogmelding.personNrLege,
                msgId = receivedDialogmelding.msgId,
                loggingMeta = loggingMeta
            )

            if (avsenderBehandler == null) {
                return ValidationResult(
                    status = Status.INVALID,
                    ruleHits = listOf(
                        RuleInfo(
                            ruleName = "BEHANDLER_NOT_IN_HPR",
                            messageForSender = "Den som har skrevet dialogmeldingen ble ikke funnet i Helsepersonellregisteret (HPR)",
                            messageForUser = "Avsender fodselsnummer er ikke registert i Helsepersonellregisteret (HPR)",
                            ruleStatus = Status.INVALID
                        )
                    )
                )
            }

            val results = listOf(
                ValidationRuleChain.values().executeFlow(
                    dialogmelding, RuleMetadata(
                        receivedDate = receivedDialogmelding.mottattDato,
                        signatureDate = receivedDialogmelding.mottattDato,
                        patientPersonNumber = receivedDialogmelding.personNrPasient,
                        legekontorOrgnr = receivedDialogmelding.legekontorOrgNr,
                        tssid = receivedDialogmelding.tssid,
                        avsenderfnr = receivedDialogmelding.personNrLege
                    )
                ),
                HPRRuleChain.values().executeFlow(dialogmelding, avsenderBehandler),
                LegesuspensjonRuleChain.values().executeFlow(dialogmelding, doctorSuspend)
            ).flatten()

            log.info("Rules hit {}, {}", results.map { it.name }, fields(loggingMeta))

            return validationResult(results)
        }

    private fun validationResult(results: List<Rule<Any>>): ValidationResult = ValidationResult(
        status = results
            .map { status -> status.status }.let {
                it.firstOrNull { status -> status == Status.INVALID }
                    ?: Status.OK
            },
        ruleHits = results.map { rule ->
            RuleInfo(
                rule.name,
                rule.messageForSender!!,
                rule.messageForUser!!,
                rule.status
            )
        }
    )
}
