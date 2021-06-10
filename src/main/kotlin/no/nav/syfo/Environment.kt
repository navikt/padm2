package no.nav.syfo

import no.nav.syfo.mq.MqConfig

data class Environment(
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "padm2"),
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val inputQueueName: String = getEnvVar("MQ_INPUT_QUEUE_NAME"),
    val apprecQueueName: String = getEnvVar("MQ_APPREC_QUEUE_NAME"),
    val aktoerregisterV1Url: String = getEnvVar("AKTOR_REGISTER_V1_URL"),
    val kuhrSarApiUrl: String = getEnvVar("KUHR_SAR_API_URL", "http://kuhr-sar-api.teamkuhr.svc.nais.local"),
    val subscriptionEndpointURL: String = getEnvVar("SUBSCRIPTION_ENDPOINT_URL"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val padm2ReglerEndpointURL: String = getEnvVar("PADM2REGLER_ENDPOINT_URL", "http://padm2regler"),
    val inputBackoutQueueName: String = getEnvVar("MQ_INPUT_BOQ_QUEUE_NAME"),
    val opprettSakUrl: String = getEnvVar("OPPRETT_SAK_URL", "http://sak/api/v1/saker"),
    val dokArkivUrl: String = getEnvVar("DOK_ARKIV_URL"),
    val securityTokenServiceURL: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL"),
    val syfopdfgen: String = getEnvVar("PDF_GEN_URL", "http://padm2pdfgen/api/v1/genpdf/padm2/padm2"),
    val arenaQueueName: String = getEnvVar("ARENA_OUTBOUND_QUEUENAME"),
    val padm2DBURL: String = getEnvVar("PADM2_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "padm2"),
    val eiaQueueName: String = getEnvVar("EIA_INBOUND_MOTTAK_QUEUE_NAME"),
    val syfohelsenettproxyEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL", "http://syfohelsenettproxy"),
    val legeSuspensjonEndpointURL: String = getEnvVar("LEGE_SUSPENSJON_ENDPOINT_URL", "http://btsys"),
    val norskHelsenettEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL", "http://syfohelsenettproxy"),
    val helsenettproxyId: String = getEnvVar("HELSENETTPROXY_ID"),
    val aadAccessTokenUrl: String = getEnvVar("AADACCESSTOKEN_URL")
) : MqConfig

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val clientId: String,
    val clientsecret: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
