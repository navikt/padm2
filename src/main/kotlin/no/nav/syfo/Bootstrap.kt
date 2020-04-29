package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import javax.jms.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.BlockingApplicationRunner
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.Padm2ReglerClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.services.JournalService
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.ws.createPort
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis

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
        redisSecret = getFileAsString("/secrets/default/redisSecret")
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

    val httpClient = HttpClient(Apache, config)

    val oidcClient = StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword)
    val aktoerIdClient = AktoerIdClient(env.aktoerregisterV1Url, oidcClient, httpClient)
    val sarClient = SarClient(env.kuhrSarApiUrl, httpClient)
    val padm2ReglerClient = Padm2ReglerClient(env.padm2ReglerEndpointURL, httpClient)
    val stsClient = StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword)
    val sakClient = SakClient(env.opprettSakUrl, stsClient, httpClient)
    val dokArkivClient = DokArkivClient(env.dokArkivUrl, stsClient, httpClient)
    val pdfgenClient = PdfgenClient(env.syfopdfgen, httpClient)

    val journalService = JournalService(sakClient, dokArkivClient, pdfgenClient)

    val subscriptionEmottak = createPort<SubscriptionPort>(env.subscriptionEndpointURL) {
        proxy { features.add(WSAddressingFeature()) }
        port { withBasicAuth(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword) }
    }

    launchListeners(applicationState, env,
        vaultSecrets, aktoerIdClient, sarClient,
        subscriptionEmottak, padm2ReglerClient, journalService)
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
    journalService: JournalService
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
                    arenaProducer
                )
            }
        }
    }
}
