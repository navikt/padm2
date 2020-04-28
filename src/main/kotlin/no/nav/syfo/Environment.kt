package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
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
    val kuhrSarApiUrl: String = getEnvVar("KUHR_SAR_API_URL", "http://kuhr-sar-api"),
    val subscriptionEndpointURL: String = getEnvVar("SUBSCRIPTION_ENDPOINT_URL"),
    val redishost: String = getEnvVar("REDIS_HOST", "padm2-redis.default.svc.nais.local"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val padm2ArenaTopic: String = getEnvVar("KAFKA_PALE_2_SAK_TOPIC", "privat-syfo-padm2-arena-v1"),
    val padm2ReglerEndpointURL: String = getEnvVar("PADM2REGLER_ENDPOINT_URL", "http://padm2regler"),
    val inputBackoutQueueName: String = getEnvVar("MQ_INPUT_BOQ_QUEUE_NAME"),
    val padm2SakTopic: String = getEnvVar("KAFKA_PADM2SAK_TOPIC", "privat-syfo-padm2sak-v1"),
    val opprettSakUrl: String = getEnvVar("OPPRETT_SAK_URL", "http://sak/api/v1/saker"),
    val dokArkivUrl: String = getEnvVar("DOK_ARKIV_URL"),
    val securityTokenServiceURL: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL"),
    val syfopdfgen: String = getEnvVar("PDF_GEN_URL", "http://syfopdfgen/api/v1/genpdf/padm2sak/padm2sak"),
    val arenaQueueName: String = getEnvVar("ARENA_OUTBOUND_QUEUENAME")
) : MqConfig, KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String,
    val redisSecret: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
