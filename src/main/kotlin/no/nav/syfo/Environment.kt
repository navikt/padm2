package no.nav.syfo

import no.nav.syfo.util.MqConfig
import no.nav.syfo.util.getFileAsString

data class Environment(
    val aadAppClient: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val aadAppSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val aadTokenEndpoint: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "padm2"),
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val inputQueueName: String = getEnvVar("MQ_INPUT_QUEUE_NAME"),
    val apprecQueueName: String = getEnvVar("MQ_APPREC_QUEUE_NAME"),
    val aktoerregisterV1Url: String = getEnvVar("AKTOR_REGISTER_V1_URL"),
    val kuhrSarApiUrl: String = getEnvVar("KUHR_SAR_API_URL"),
    val subscriptionEndpointURL: String = getEnvVar("SUBSCRIPTION_ENDPOINT_URL"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val inputBackoutQueueName: String = getEnvVar("MQ_INPUT_BOQ_QUEUE_NAME"),
    val dokArkivClientId: String = getEnvVar("DOKARKIV_CLIENT_ID"),
    val dokArkivUrl: String = getEnvVar("DOK_ARKIV_URL"),
    val syfopdfgen: String = getEnvVar("PDF_GEN_URL"),
    val arenaQueueName: String = getEnvVar("ARENA_OUTBOUND_QUEUENAME"),
    val padm2DBURL: String = getEnvVar("PADM2_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "padm2"),
    val syfohelsenettproxyEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL"),
    val legeSuspensjonEndpointURL: String = getEnvVar("LEGE_SUSPENSJON_ENDPOINT_URL"),
    val helsenettClientId: String = getEnvVar("HELSENETT_CLIENT_ID"),
    val stsUrl: String = getEnvVar("STS_URL"),
    val kafka: ApplicationEnvironmentKafka = ApplicationEnvironmentKafka(
        bootstrapServers = getEnvVar("KAFKA_BROKERS"),
        aivenCredstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
        aivenTruststoreLocation = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
        aivenKeystoreLocation = getEnvVar("KAFKA_KEYSTORE_PATH"),
    ),
    val toggleDialogmeldingerTilKafka: Boolean = getEnvVar("TOGGLE_DIALOGMELDINGER_TIL_KAFKA").toBoolean(),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password"),
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
) : MqConfig

data class ApplicationEnvironmentKafka(
    val bootstrapServers: String,
    val aivenCredstorePassword: String,
    val aivenTruststoreLocation: String,
    val aivenKeystoreLocation: String,
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
