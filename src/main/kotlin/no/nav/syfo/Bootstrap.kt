package no.nav.syfo

import io.ktor.application.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.BlockingApplicationRunner
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.client.*
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.kafka.*
import no.nav.syfo.services.JournalService
import no.nav.syfo.services.RuleService
import no.nav.syfo.services.SignerendeLegeService
import no.nav.syfo.util.*
import no.nav.syfo.vault.RenewVaultService
import no.nav.syfo.ws.createPort
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import javax.jms.Session

val logger: Logger = LoggerFactory.getLogger("no.nav.syfo.padm2")

fun main() {
    val env = Environment()

    val applicationState = ApplicationState()

    val vaultSecrets = VaultSecrets(
        serviceuserUsername = env.serviceuserUsername,
        serviceuserPassword = env.serviceuserPassword,
    )

    val vaultCredentialService = VaultCredentialService()

    val database = Database(
        env = env,
        vaultCredentialService = vaultCredentialService,
    )

    val applicationEngineEnvironment = applicationEngineEnvironment {
        log = logger
        connector {
            port = env.applicationPort
        }
        module {
            routing {
                registerNaisApi(applicationState)
            }
        }
    }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
        logger.info("Application is ready")
        val renewVaultService = RenewVaultService(
            vaultCredentialService = vaultCredentialService,
            applicationState = applicationState,
        )
        renewVaultService.startRenewTasks()

        launchListeners(
            applicationState = applicationState,
            env = env,
            vaultSecrets = vaultSecrets,
            database = database,
        )
    }

    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )
    server.start(false)
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            logger.error("En uhåndtert feil oppstod, applikasjonen restarter {}", e.cause)
        } catch (t: Throwable) {
            logger.error("En uhåndtert systemfeil oppstod, applikasjonen restarter {}", t.message)
            throw t
        } finally {
            applicationState.alive = false
        }
    }

fun launchListeners(
    applicationState: ApplicationState,
    env: Environment,
    vaultSecrets: VaultSecrets,
    database: Database,
) {
    createListener(applicationState) {

        val oidcClient = StsOidcClient(
            username = vaultSecrets.serviceuserUsername,
            password = vaultSecrets.serviceuserPassword,
            stsUrl = env.stsUrl,
        )

        val aktoerIdClient = AktoerIdClient(
            endpointUrl = env.aktoerregisterV1Url,
            stsClient = oidcClient,
            httpClient = httpClient,
            serviceUsername = vaultSecrets.serviceuserUsername,
        )

        val kuhrSarClient = SarClient(
            endpointUrl = env.kuhrSarApiUrl,
            httpClient = httpClient,
        )

        val pdfgenClient = PdfgenClient(
            url = env.syfopdfgen,
            httpClient = httpClient,
        )

        val azureAdV2Client = AzureAdV2Client(
            aadAppClient = env.aadAppClient,
            aadAppSecret = env.aadAppSecret,
            aadTokenEndpoint = env.aadTokenEndpoint,
            httpClient = httpClientWithProxy,
        )

        val dokArkivClient = DokArkivClient(
            azureAdV2Client = azureAdV2Client,
            dokArkivClientId = env.dokArkivClientId,
            url = env.dokArkivUrl,
            httpClient = httpClientWithProxy,
        )

        val syfohelsenettproxyClient = SyfohelsenettproxyClient(
            azureAdV2Client = azureAdV2Client,
            endpointUrl = env.syfohelsenettproxyEndpointURL,
            httpClient = httpClient,
            helsenettClientId = env.helsenettClientId,
        )

        val padm2ReglerService = RuleService(
            legeSuspensjonClient = LegeSuspensjonClient(
                endpointUrl = env.legeSuspensjonEndpointURL,
                secrets = vaultSecrets,
                stsClient = oidcClient,
                httpClient = httpClient,
            ),
            syfohelsenettproxyClient = syfohelsenettproxyClient,
        )

        val journalService = JournalService(
            dokArkivClient = dokArkivClient,
            pdfgenClient = pdfgenClient,
        )

        val signerendeLegeService = SignerendeLegeService(
            syfohelsenettproxyClient = syfohelsenettproxyClient,
        )

        val subscriptionEmottak = createPort<SubscriptionPort>(env.subscriptionEndpointURL) {
            proxy { features.add(WSAddressingFeature()) }
            port { withBasicAuth(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword) }
        }

        val factory = connectionFactory(env)

        factory.createConnection(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword).use { connection ->
            connection.start()
            val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

            val inputconsumer = session.consumerForQueue(env.inputQueueName)
            val receiptProducer = session.producerForQueue(env.apprecQueueName)
            val backoutProducer = session.producerForQueue(env.inputBackoutQueueName)
            val arenaProducer = session.producerForQueue(env.arenaQueueName)
            val dialogmeldingProducer = DialogmeldingProducer(
                kafkaProducerDialogmelding = KafkaProducer<String, DialogmeldingForKafka>(
                    kafkaDialogmeldingProducerConfig(env)
                ),
                enabled = env.toggleDialogmeldingerTilKafka,
            )

            BlockingApplicationRunner(
                applicationState = applicationState,
                database = database,
                inputconsumer = inputconsumer,
                session = session,
                env = env,
                aktoerIdClient = aktoerIdClient,
                kuhrSarClient = kuhrSarClient,
                subscriptionEmottak = subscriptionEmottak,
                receiptProducer = receiptProducer,
                padm2ReglerService = padm2ReglerService,
                backoutProducer = backoutProducer,
                journalService = journalService,
                arenaProducer = arenaProducer,
                signerendeLegeService = signerendeLegeService,
                dialogmeldingProducer = dialogmeldingProducer,
            ).run()
        }
    }
}
