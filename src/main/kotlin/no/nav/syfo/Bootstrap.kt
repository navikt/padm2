package no.nav.syfo

import io.ktor.application.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.syfo.application.*
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.application.mq.*
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.kafka.*
import no.nav.syfo.vault.RenewVaultService
import no.nav.syfo.ws.createPort
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import javax.jms.Session

val logger: Logger = LoggerFactory.getLogger("no.nav.syfo.padm2")

fun main() {
    logger.info("Padm2 starting with java version: " + Runtime.version())
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

fun launchListeners(
    applicationState: ApplicationState,
    env: Environment,
    database: Database,
) {
    val dialogmeldingProducer = DialogmeldingProducer(
        kafkaProducerDialogmelding = KafkaProducer<String, DialogmeldingForKafka>(
            kafkaDialogmeldingProducerConfig(env.kafka)
        ),
        enabled = env.toggleDialogmeldingerTilKafka,
    )
    val subscriptionEmottak = createPort<SubscriptionPort>(env.subscriptionEndpointURL) {
        port { withBasicAuth(env.serviceuserUsername, env.serviceuserPassword) }
    }

    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        val factory = connectionFactory(env)

        factory.createConnection(env.serviceuserUsername, env.serviceuserPassword).use { connection ->
            connection.start()
            val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            val inputconsumer = session.consumerForQueue(env.inputQueueName)
            val mqSender = MQSender(
                env = env,
            )
            val blockingApplicationRunner = BlockingApplicationRunner(
                applicationState = applicationState,
                database = database,
                env = env,
                inputconsumer = inputconsumer,
                mqSender = mqSender,
                dialogmeldingProducer = dialogmeldingProducer,
                subscriptionEmottak = subscriptionEmottak,
            )
            blockingApplicationRunner.run()
        }
    }

    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        val mqSender = MQSender(
            env = env,
        )
        val dialogmeldingProcessor = DialogmeldingProcessor(
            database = database,
            env = env,
            mqSender = mqSender,
            dialogmeldingProducer = dialogmeldingProducer,
            subscriptionEmottak = subscriptionEmottak,
        )
        val rerunCronJob = RerunCronJob(
            database = database,
            dialogmeldingProcessor = dialogmeldingProcessor,
        )
        CronjobRunner(
            applicationState = applicationState,
        ).start(cronjob = rerunCronJob)
    }
}
