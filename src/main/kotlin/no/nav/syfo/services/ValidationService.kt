package no.nav.syfo.services

import io.ktor.client.HttpClient
import no.nav.syfo.Environment
import no.nav.syfo.client.ClamAvClient
import no.nav.syfo.client.LegeSuspensjonClient
import no.nav.syfo.client.SyfohelsenettproxyClient
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.handlestatus.*
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.model.isHenvendelseFraLegeOrForesporselSvar
import no.nav.syfo.persistering.db.hentMottattTidspunkt
import no.nav.syfo.util.*
import no.nav.syfo.validation.isKodeverkValid

interface ValidationService {
    suspend fun validate(
        receivedDialogmelding: ReceivedDialogmelding,
        vedlegg: List<Vedlegg>,
        loggingMeta: LoggingMeta,
    ): ValidationResult
}

class Padm2ValidationService(
    private val database: DatabaseInterface,
    private val env: Environment,
    azureAdV2Client: AzureAdV2Client,
    httpClient: HttpClient,
) : ValidationService {
    private val pdlClient = PdlClient(
        azureAdV2Client = azureAdV2Client,
        pdlClientId = env.pdlClientId,
        pdlUrl = env.pdlUrl,
        httpClient = httpClient,
    )
    private val virusScanService = VirusScanService(
        clamAvClient = ClamAvClient(
            endpointUrl = env.clamavURL,
            httpClient = httpClient,
        ),
    )
    private val ruleService = RuleService(
        legeSuspensjonClient = LegeSuspensjonClient(
            azureAdV2Client = azureAdV2Client,
            endpointUrl = env.legeSuspensjonEndpointURL,
            endpointClientId = env.legeSuspensjonClientId,
            applicationName = env.applicationName,
            httpClient = httpClient,
        ),
        syfohelsenettproxyClient = SyfohelsenettproxyClient(
            azureAdV2Client = azureAdV2Client,
            endpointUrl = env.syfohelsenettproxyEndpointURL,
            helsenettClientId = env.syfohelsenettproxyClientId,
            httpClient = httpClient,
        ),
        env = env,
    )

    override suspend fun validate(
        receivedDialogmelding: ReceivedDialogmelding,
        vedlegg: List<Vedlegg>,
        loggingMeta: LoggingMeta,
    ): ValidationResult {
        val fellesformat = safeUnmarshal(receivedDialogmelding.fellesformat)
        val dialogmeldingXml = extractDialogmelding(fellesformat)
        val sha256String = sha256hashstring(
            dialogmeldingXml,
            extractPatient(fellesformat),
            extractValidVedlegg(fellesformat),
        )
        val dialogmeldingType = receivedDialogmelding.dialogmeldingType

        val innbyggerOK = pdlClient.personEksisterer(PersonIdent(receivedDialogmelding.personNrPasient))
        val legeOK = pdlClient.personEksisterer(PersonIdent(receivedDialogmelding.personNrLege))

        val initialValidationResult: ValidationResult? =
            if (dialogmeldingDokumentWithShaExists(receivedDialogmelding.dialogmelding.id, sha256String, database)) {
                val tidMottattOpprinneligMelding = database.hentMottattTidspunkt(sha256String)
                handleDuplicateDialogmeldingContent(
                    loggingMeta,
                    sha256String,
                    tidMottattOpprinneligMelding,
                )
            } else if (!innbyggerOK) {
                handlePatientNotFound(loggingMeta)
            } else if (!legeOK && !env.isDevGcp) {
                handleBehandlerNotFound(loggingMeta)
            } else if (erTestFnr(receivedDialogmelding.personNrPasient) && env.cluster == "prod-gcp") {
                handleTestFnrInProd(loggingMeta)
            } else if (dialogmeldingType.isHenvendelseFraLegeOrForesporselSvar() && dialogmeldingXml.notat.first().tekstNotatInnhold.isNullOrEmpty()) {
                handleMeldingsTekstMangler(loggingMeta)
            } else if (!isKodeverkValid(receivedDialogmelding.msgId, dialogmeldingXml, dialogmeldingType)) {
                handleInvalidDialogMeldingKodeverk(loggingMeta)
            } else if (virusScanService.vedleggContainsVirus(vedlegg)) {
                handleVedleggMayContainVirus(loggingMeta)
            } else {
                null
            }

        return initialValidationResult ?: ruleService.executeRuleChains(
            receivedDialogmelding = receivedDialogmelding,
        )
    }
}

/**
 * For meldinger som allerede er validert av avsendersystemet (Team Helsemelding).
 */
class PreValidatedService : ValidationService {
    override suspend fun validate(
        receivedDialogmelding: ReceivedDialogmelding,
        vedlegg: List<Vedlegg>,
        loggingMeta: LoggingMeta,
    ): ValidationResult = ValidationResult(Status.OK, emptyList())
}
