package no.nav.syfo

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.mq.*
import no.nav.syfo.client.SmgcpClient
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.httpClient
import no.nav.syfo.client.httpClientWithProxy
import no.nav.syfo.client.wellknown.getWellKnown
import no.nav.syfo.db.Database
import no.nav.syfo.kafka.*
import no.nav.syfo.services.EmottakService
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import javax.jms.Session

val logger: Logger = LoggerFactory.getLogger("no.nav.syfo.padm2")

fun main() {
    logger.info("Padm2 starting with java version: " + Runtime.version())
    val env = Environment()

    setMQTlsProperties(env)

    val applicationState = ApplicationState()

    val wellKnownInternalAzureAD = getWellKnown(
        wellKnownUrl = env.aadAppWellKnownUrl,
    )

    val database = Database(
        env = env,
    )

    val applicationEngineEnvironment = applicationEngineEnvironment {
        log = logger
        connector {
            port = env.applicationPort
        }
        module {
            apiModule(
                applicationState = applicationState,
                database = database,
                environment = env,
                wellKnownInternalAzureAD = wellKnownInternalAzureAD,
            )
        }
    }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
        logger.info("Application is ready")

        launchListeners(
            applicationState = applicationState,
            env = env,
            database = database,
        )
    }

    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
    ) {
        connectionGroupSize = 8
        workerGroupSize = 8
        callGroupSize = 16
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )
    server.start(wait = true)
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
    )

    val mqSender = MQSender(
        env = env,
    )

    val azureAdV2Client = AzureAdV2Client(
        aadAppClient = env.aadAppClient,
        aadAppSecret = env.aadAppSecret,
        aadTokenEndpoint = env.aadTokenEndpoint,
        httpClient = httpClientWithProxy,
    )

    val smgcpClient = SmgcpClient(
        azureAdV2Client = azureAdV2Client,
        url = env.smgcpProxyUrl,
        smgcpClientId = env.smgcpProxyClientId,
        httpClient = httpClient,
    )

    val smtssClient = SmtssClient(
        azureAdV2Client = azureAdV2Client,
        smtssClientId = env.smtssClientId,
        smtssUrl = env.smtssApiUrl,
        httpClient = httpClient,
    )

    val emottakService = EmottakService(
        smgcpClient = smgcpClient,
    )

    val dialogmeldingProcessor = DialogmeldingProcessor(
        database = database,
        env = env,
        mqSender = mqSender,
        dialogmeldingProducer = dialogmeldingProducer,
        azureAdV2Client = azureAdV2Client,
        smtssClient = smtssClient,
        emottakService = emottakService,
    )

    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        val factory = connectionFactory(env)

        factory.createConnection(env.serviceuserUsername, env.serviceuserPassword).use { connection ->
            connection.start()
            val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            val inputconsumer = session.consumerForQueue(env.inputQueueName)

            val blockingApplicationRunner = BlockingApplicationRunner(
                applicationState = applicationState,
                database = database,
                inputconsumer = inputconsumer,
                mqSender = mqSender,
                dialogmeldingProcessor = dialogmeldingProcessor,
            )
            blockingApplicationRunner.run()
        }
    }

    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        val rerunCronJob = RerunCronJob(
            database = database,
            dialogmeldingProcessor = dialogmeldingProcessor,
        )
        CronjobRunner(
            applicationState = applicationState,
        ).start(cronjob = rerunCronJob)
    }
}

private fun setMQTlsProperties(env: Environment) {
    System.setProperty("javax.net.ssl.keyStore", env.mqKeystorePath)
    System.setProperty("javax.net.ssl.keyStorePassword", env.mqKeystorePassword)
    System.setProperty("javax.net.ssl.keyStoreType", "jks")
}
