package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.services.BehandlerService
import no.nav.syfo.services.JournalService
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.vault.RenewVaultService
import no.nav.syfo.ws.createPort
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
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
        mqUsername = getFileAsString("/secrets/default/mqUsername"),
        mqPassword = getFileAsString("/secrets/default/mqPassword"),
        redisSecret = getFileAsString("/secrets/default/redisSecret"),
        clientId = getFileAsString("/secrets/azuread/padm2/client_id"),
        clientsecret = getFileAsString("/secrets/azuread/padm2/client_secret")
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

    val oidcClient = StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword)
    val aktoerIdClient = AktoerIdClient(env.aktoerregisterV1Url, oidcClient, httpClient)
    val sarClient = SarClient(env.kuhrSarApiUrl, httpClient)
    val padm2ReglerClient = Padm2ReglerClient(env.padm2ReglerEndpointURL, httpClient)
    val stsClient = StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword)
    val sakClient = SakClient(env.opprettSakUrl, stsClient, httpClient)
    val dokArkivClient = DokArkivClient(env.dokArkivUrl, stsClient, httpClient)
    val pdfgenClient = PdfgenClient(env.syfopdfgen, httpClient)
    val accessTokenClient = AccessTokenClient(env.aadAccessTokenUrl, vaultSecrets.clientId, vaultSecrets.clientsecret, httpClientWithProxy)
    val syfohelsenettproxyClient = SyfohelsenettproxyClient(env.syfohelsenettproxyEndpointURL, accessTokenClient, env.helsenettproxyId, httpClient)

    val journalService = JournalService(sakClient, dokArkivClient, pdfgenClient)
    val behandlerService = BehandlerService(syfohelsenettproxyClient)

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
        subscriptionEmottak, padm2ReglerClient, journalService, database, behandlerService
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
    padm2ReglerClient: Padm2ReglerClient,
    journalService: JournalService,
    database: Database,
    behandlerService: BehandlerService
) {
    createListener(applicationState) {
        connectionFactory(env).createConnection(secrets.mqUsername, secrets.mqPassword).use { connection ->
            Jedis(env.redishost, 6379).use { jedis ->
                connection.start()
                val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

                val inputconsumer = session.consumerForQueue(env.inputQueueName)
                val receiptProducer = session.producerForQueue(env.apprecQueueName)
                val backoutProducer = session.producerForQueue(env.inputBackoutQueueName)
                val arenaProducer = session.producerForQueue(env.arenaQueueName)

                applicationState.ready = true
                jedis.auth(secrets.redisSecret)

                BlockingApplicationRunner().run(
                    applicationState, inputconsumer,
                    session, env, secrets, aktoerIdClient,
                    kuhrSarClient, subscriptionEmottak, jedis, receiptProducer,
                    padm2ReglerClient, backoutProducer, journalService,
                    arenaProducer, database, behandlerService
                )
            }
        }
    }
}
