package no.nav.syfo

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.api.access.PreAuthorizedClient
import no.nav.syfo.util.configuredJacksonMapper
import java.net.ServerSocket

fun testEnvironment(
    kafkaBootstrapServers: String,
    azureTokenEndpoint: String = "azureTokenEndpoint",
    clamAvURL: String,
    dokarkivUrl: String,
    pdfgenUrl: String,
    pdlUrl: String,
    syfohelsenettproxyEndpointUrl: String,
    legeSuspensjonEndpointUrl: String,
    smtssUrl: String,
    smgcpUrl: String,
    isbehandlerdialogUrl: String,
) = Environment(
    aadAppClient = "isdialogmote-client-id",
    aadAppSecret = "isdialogmote-secret",
    aadTokenEndpoint = azureTokenEndpoint,
    aadAppPreAuthorizedApps = configuredJacksonMapper().writeValueAsString(testAzureAppPreAuthorizedApps),
    aadAppWellKnownUrl = "wellknown",
    databaseHost = "localhost",
    databasePort = "5432",
    databaseName = "padm2_dev",
    databaseUsername = "username",
    databasePassword = "password",
    cluster = "dev-gcp",
    kafka = ApplicationEnvironmentKafka(
        bootstrapServers = kafkaBootstrapServers,
        aivenCredstorePassword = "credstorepassord",
        aivenTruststoreLocation = "truststore",
        aivenKeystoreLocation = "keystore",
    ),
    serviceuserUsername = "user",
    serviceuserPassword = "password",
    clamavURL = clamAvURL,
    dokArkivClientId = "dokarkiv",
    dokArkivUrl = dokarkivUrl,
    syfopdfgen = pdfgenUrl,
    mqHostname = "mqhost",
    mqPort = 1234,
    mqGatewayName = "mqGateway",
    mqChannelName = "mqChannel",
    mqKeystorePassword = "pw",
    mqKeystorePath = "",
    inputQueueName = "inputQueue",
    arenaQueueName = "arenaQueue",
    apprecQueueName = "apprecQueue",
    inputBackoutQueueName = "backoutQueue",
    pdlClientId = "pdl",
    pdlUrl = pdlUrl,
    syfohelsenettproxyClientId = "helsenett",
    syfohelsenettproxyEndpointURL = syfohelsenettproxyEndpointUrl,
    legeSuspensjonClientId = "legesuspensjon",
    legeSuspensjonEndpointURL = legeSuspensjonEndpointUrl,
    smtssClientId = "smtss",
    smtssApiUrl = smtssUrl,
    smgcpProxyClientId = "smgcp",
    smgcpProxyUrl = smgcpUrl,
    isbehandlerdialogClientId = "isbehandlerdialog",
    isbehandlerdialogUrl = isbehandlerdialogUrl,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true
)

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}

const val testIsBehandlerDialogClientId = "isbehandlerdialog-client-id"

val testAzureAppPreAuthorizedApps = listOf(
    PreAuthorizedClient(
        name = "dev-gcp:teamsykefravr:isbehandlerdialog",
        clientId = testIsBehandlerDialogClientId,
    ),
)
