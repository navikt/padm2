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
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.kafka.*
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
    database: Database,
) {
    createListener(applicationState) {

        val factory = connectionFactory(env)

        factory.createConnection(env.serviceuserUsername, env.serviceuserPassword).use { connection ->
            connection.start()
            val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

            val inputconsumer = session.consumerForQueue(env.inputQueueName)
            val receiptProducer = session.producerForQueue(env.apprecQueueName)
            val backoutProducer = session.producerForQueue(env.inputBackoutQueueName)
            val arenaProducer = session.producerForQueue(env.arenaQueueName)
            val dialogmeldingProducer = DialogmeldingProducer(
                kafkaProducerDialogmelding = KafkaProducer<String, DialogmeldingForKafka>(
                    kafkaDialogmeldingProducerConfig(env.kafka)
                ),
                enabled = env.toggleDialogmeldingerTilKafka,
            )
            val subscriptionEmottak = createPort<SubscriptionPort>(env.subscriptionEndpointURL) {
                proxy { features.add(WSAddressingFeature()) }
                port { withBasicAuth(env.serviceuserUsername, env.serviceuserPassword) }
            }

            BlockingApplicationRunner(
                applicationState = applicationState,
                database = database,
                env = env,
                inputconsumer = inputconsumer,
                session = session,
                receiptProducer = receiptProducer,
                backoutProducer = backoutProducer,
                arenaProducer = arenaProducer,
                dialogmeldingProducer = dialogmeldingProducer,
                subscriptionEmottak = subscriptionEmottak,
            ).run()
        }
    }
}
