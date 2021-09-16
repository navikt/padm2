package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.ibm.msg.client.jms.JmsConstants.USER_AUTHENTICATION_MQCSP
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.util.*
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.BlockingApplicationRunner
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.client.*
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.services.JournalService
import no.nav.syfo.services.RuleService
import no.nav.syfo.services.SignerendeLegeService
import no.nav.syfo.util.*
import no.nav.syfo.vault.RenewVaultService
import no.nav.syfo.ws.createPort
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ProxySelector
import javax.jms.Session

val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.padm2")

@KtorExperimentalAPI
fun main() {
    val env = Environment()

    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )

    val vaultSecrets = VaultSecrets(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username"),
        clientId = getFileAsString("/secrets/azuread/padm2/client_id"),
        clientsecret = getFileAsString("/secrets/azuread/padm2/client_secret"),
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()

    DefaultExports.initialize()

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
    }

    val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }

    val httpClient = HttpClient(Apache, config)
    val httpClientWithProxy = HttpClient(Apache, proxyConfig)

    val oidcClient = StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword, env.stsUrl)
    val aktoerIdClient = AktoerIdClient(env.aktoerregisterV1Url, oidcClient, httpClient)
    val sarClient = SarClient(env.kuhrSarApiUrl, httpClient)

    val stsClient = StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword, env.stsUrl)
    val sakClient = SakClient(env.opprettSakUrl, stsClient, httpClient)
    val dokArkivClient = DokArkivClient(env.dokArkivUrl, stsClient, httpClient)
    val pdfgenClient = PdfgenClient(env.syfopdfgen, httpClient)
    val accessTokenClient =
        AccessTokenClient(env.aadAccessTokenUrl, vaultSecrets.clientId, vaultSecrets.clientsecret, httpClientWithProxy)

    val syfohelsenettproxyClient = SyfohelsenettproxyClient(
        env.syfohelsenettproxyEndpointURL,
        accessTokenClient,
        env.helsenettproxyId,
        httpClient
    )

    val ruleService = RuleService(
        LegeSuspensjonClient(env.legeSuspensjonEndpointURL, vaultSecrets, oidcClient, httpClient),
        syfohelsenettproxyClient
    )

    val journalService = JournalService(sakClient, dokArkivClient, pdfgenClient)
    val signerendeLegeService = SignerendeLegeService(syfohelsenettproxyClient)

    val subscriptionEmottak = createPort<SubscriptionPort>(env.subscriptionEndpointURL) {
        proxy { features.add(WSAddressingFeature()) }
        port { withBasicAuth(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword) }
    }

    val vaultCredentialService = VaultCredentialService()
    val database = Database(env, vaultCredentialService)

    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    launchListeners(
        applicationState, env,
        vaultSecrets, aktoerIdClient, sarClient,
        subscriptionEmottak, ruleService, journalService, database, signerendeLegeService
    )
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", e.cause)
        } finally {
            applicationState.alive = false
        }
    }

@KtorExperimentalAPI
fun launchListeners(
    applicationState: ApplicationState,
    env: Environment,
    secrets: VaultSecrets,
    aktoerIdClient: AktoerIdClient,
    kuhrSarClient: SarClient,
    subscriptionEmottak: SubscriptionPort,
    padm2ReglerService: RuleService,
    journalService: JournalService,
    database: Database,
    signerendeLegeService: SignerendeLegeService
) {
    createListener(applicationState) {
        val factory = connectionFactory(env)
        factory.setBooleanProperty(USER_AUTHENTICATION_MQCSP, true)

        factory.createConnection(secrets.serviceuserUsername, secrets.serviceuserPassword).use { connection ->
            connection.start()
            val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

            val inputconsumer = session.consumerForQueue(env.inputQueueName)
            val receiptProducer = session.producerForQueue(env.apprecQueueName)
            val backoutProducer = session.producerForQueue(env.inputBackoutQueueName)
            val arenaProducer = session.producerForQueue(env.arenaQueueName)

            applicationState.ready = true

            BlockingApplicationRunner().run(
                applicationState, inputconsumer,
                session, env, secrets, aktoerIdClient,
                kuhrSarClient, subscriptionEmottak, receiptProducer,
                padm2ReglerService, backoutProducer, journalService,
                arenaProducer, database, signerendeLegeService
            )
        }
    }
}
