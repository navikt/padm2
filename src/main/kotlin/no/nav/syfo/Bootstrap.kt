package no.nav.syfo

import io.ktor.application.ApplicationStarted
import io.ktor.routing.routing
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.vault.RenewVaultService
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import javax.jms.Session
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.BlockingApplicationRunner
import no.nav.syfo.application.CronjobRunner
import no.nav.syfo.application.DialogmeldingProcessor
import no.nav.syfo.application.RerunCronJob
import no.nav.syfo.application.launchBackgroundTask
import no.nav.syfo.application.mq.MQSender
import no.nav.syfo.application.mq.connectionFactory
import no.nav.syfo.application.mq.consumerForQueue
import no.nav.syfo.kafka.DialogmeldingForKafka
import no.nav.syfo.kafka.DialogmeldingProducer
import no.nav.syfo.kafka.kafkaDialogmeldingProducerConfig

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
