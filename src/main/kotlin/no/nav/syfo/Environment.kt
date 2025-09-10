package no.nav.syfo

import no.nav.syfo.application.mq.MqConfig

data class Environment(
    val aadAppClient: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val aadAppSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val aadTokenEndpoint: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val aadAppPreAuthorizedApps: String = getEnvVar("AZURE_APP_PRE_AUTHORIZED_APPS"),
    val aadAppWellKnownUrl: String = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "padm2"),
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val inputQueueName: String = getEnvVar("MQ_INPUT_QUEUE_NAME"),
    val apprecQueueName: String = getEnvVar("MQ_APPREC_QUEUE_NAME"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val inputBackoutQueueName: String = getEnvVar("MQ_INPUT_BOQ_QUEUE_NAME"),
    val dokArkivClientId: String = getEnvVar("DOKARKIV_CLIENT_ID"),
    val dokArkivUrl: String = getEnvVar("DOK_ARKIV_URL"),
    val jpRetryEnabled: Boolean = getEnvVar("JP_RETRY_ENABLED").toBoolean(),
    val syfopdfgen: String = getEnvVar("PDF_GEN_URL"),
    val arenaQueueName: String = getEnvVar("ARENA_OUTBOUND_QUEUENAME"),
    val databaseHost: String = getEnvVar("NAIS_DATABASE_PADM2_PADM2_DB_HOST"),
    val databasePort: String = getEnvVar("NAIS_DATABASE_PADM2_PADM2_DB_PORT"),
    val databaseName: String = getEnvVar("NAIS_DATABASE_PADM2_PADM2_DB_DATABASE"),
    val databaseUsername: String = getEnvVar("NAIS_DATABASE_PADM2_PADM2_DB_USERNAME"),
    val databasePassword: String = getEnvVar("NAIS_DATABASE_PADM2_PADM2_DB_PASSWORD"),
    val clamavURL: String = getEnvVar("CLAMAV_URL"),
    val syfohelsenettproxyClientId: String = getEnvVar("HELSENETT_CLIENT_ID"),
    val syfohelsenettproxyEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL"),
    val legeSuspensjonClientId: String = getEnvVar("LEGE_SUSPENSJON_CLIENT_ID"),
    val legeSuspensjonEndpointURL: String = getEnvVar("LEGE_SUSPENSJON_ENDPOINT_URL"),
    val pdlClientId: String = getEnvVar("PDL_CLIENT_ID"),
    val pdlUrl: String = getEnvVar("PDL_ENDPOINT_URL"),
    val kafka: ApplicationEnvironmentKafka = ApplicationEnvironmentKafka(
        bootstrapServers = getEnvVar("KAFKA_BROKERS"),
        aivenCredstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
        aivenTruststoreLocation = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
        aivenKeystoreLocation = getEnvVar("KAFKA_KEYSTORE_PATH"),
    ),
    val serviceuserUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val serviceuserPassword: String = getEnvVar("SERVICEUSER_PASSWORD"),
    private val isbehandlerdialogApplicationName: String = "isbehandlerdialog",
    private val dokumentinnhentingApplicationName: String = "dokumentinnhenting",
    val systemAPIAuthorizedConsumerApplicationNames: List<String> = listOf(
        isbehandlerdialogApplicationName,
        dokumentinnhentingApplicationName,
    ),
    val smtssApiUrl: String = getEnvVar("SMTSS_URL"),
    val smtssClientId: String = getEnvVar("SMTSS_CLIENT_ID"),
    val smgcpProxyUrl: String = getEnvVar("SMGCP_PROXY_URL"),
    val smgcpProxyClientId: String = getEnvVar("SMGCP_PROXY_CLIENT_ID"),
    val isbehandlerdialogClientId: String = getEnvVar("ISBEHANDLERDIALOG_CLIENT_ID"),
    val isbehandlerdialogUrl: String = getEnvVar("ISBEHANDLERDIALOG_ENDPOINT_URL"),
) : MqConfig {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$databaseHost:$databasePort/$databaseName"
    }
}

data class ApplicationEnvironmentKafka(
    val bootstrapServers: String,
    val aivenCredstorePassword: String,
    val aivenTruststoreLocation: String,
    val aivenKeystoreLocation: String,
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
