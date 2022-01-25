package no.nav.syfo

import no.nav.syfo.application.ApplicationState
import java.net.ServerSocket
import java.util.*

fun testEnvironment(
    kafkaBootstrapServers: String,
    azureTokenEndpoint: String = "azureTokenEndpoint",
    dokarkivUrl: String,
    pdfgenUrl: String,
    pdlUrl: String,
    kuhrSarApiUrl: String,
    syfohelsenettproxyEndpointUrl: String,
    legeSuspensjonEndpointUrl: String,
    stsClientUrl: String,
) = Environment(
    aadAppClient = "isdialogmote-client-id",
    aadAppSecret = "isdialogmote-secret",
    aadTokenEndpoint = azureTokenEndpoint,
    padm2DBURL = "db_url",
    cluster = "dev-fss",
    mountPathVault = "vault",
    kafka = ApplicationEnvironmentKafka(
        bootstrapServers = kafkaBootstrapServers,
        aivenCredstorePassword = "credstorepassord",
        aivenTruststoreLocation = "truststore",
        aivenKeystoreLocation = "keystore",
    ),
    serviceuserUsername = "user",
    serviceuserPassword = "password",
    dokArkivClientId = "dokarkiv",
    dokArkivUrl = dokarkivUrl,
    syfopdfgen = pdfgenUrl,
    mqHostname = "mqhost",
    mqPort = 1234,
    mqGatewayName = "mqGateway",
    mqChannelName = "mqChannel",
    inputQueueName = "inputQueue",
    arenaQueueName = "arenaQueue",
    apprecQueueName = "apprecQueue",
    inputBackoutQueueName = "backoutQueue",
    pdlClientId = "pdl",
    pdlUrl = pdlUrl,
    kuhrSarApiUrl = kuhrSarApiUrl,
    subscriptionEndpointURL = "subscriptionEndpointURL",
    helsenettClientId = "helsenett",
    syfohelsenettproxyEndpointURL = syfohelsenettproxyEndpointUrl,
    legeSuspensjonEndpointURL = legeSuspensjonEndpointUrl,
    stsUrl = stsClientUrl,
    toggleDialogmeldingerTilKafka = true,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true
)

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}

fun Properties.overrideForTest(): Properties = apply {
    remove("security.protocol")
    remove("sasl.mechanism")
}
