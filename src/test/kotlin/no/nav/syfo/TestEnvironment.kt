package no.nav.syfo

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.api.access.PreAuthorizedClient
import no.nav.syfo.util.configuredJacksonMapper

fun testEnvironment() = Environment(
    aadAppClient = "isdialogmote-client-id",
    aadAppSecret = "isdialogmote-secret",
    aadTokenEndpoint = "http://azureadv2",
    aadAppPreAuthorizedApps = configuredJacksonMapper().writeValueAsString(testAzureAppPreAuthorizedApps),
    aadAppWellKnownUrl = "wellknown",
    databaseHost = "localhost",
    databasePort = "5432",
    databaseName = "padm2_dev",
    databaseUsername = "username",
    databasePassword = "password",
    cluster = "dev-gcp",
    kafka = ApplicationEnvironmentKafka(
        bootstrapServers = "kafkaBootstrapServers",
        aivenCredstorePassword = "credstorepassord",
        aivenTruststoreLocation = "truststore",
        aivenKeystoreLocation = "keystore",
    ),
    serviceuserUsername = "user",
    serviceuserPassword = "password",
    clamavURL = "http://clamav",
    dokArkivClientId = "dokarkiv",
    dokArkivUrl = "http://dokarkiv",
    syfopdfgen = "http://pdfgen",
    mqHostname = "mqhost",
    mqPort = 1234,
    mqGatewayName = "mqGateway",
    mqChannelName = "mqChannel",
    inputQueueName = "inputQueue",
    arenaQueueName = "arenaQueue",
    apprecQueueName = "apprecQueue",
    inputBackoutQueueName = "backoutQueue",
    pdlClientId = "pdl",
    pdlUrl = "http://pdl",
    syfohelsenettproxyClientId = "helsenett",
    syfohelsenettproxyEndpointURL = "http://helsenettproxy",
    legeSuspensjonClientId = "legesuspensjon",
    legeSuspensjonEndpointURL = "http://legesuspensjon",
    smtssClientId = "smtss",
    smtssApiUrl = "http://smtss",
    smgcpProxyClientId = "smgcp",
    smgcpProxyUrl = "http://smgcp",
    isbehandlerdialogClientId = "isbehandlerdialog",
    isbehandlerdialogUrl = "http://isbehandlerdialog",
    jpRetryEnabled = true,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true
)

const val testIsBehandlerDialogClientId = "isbehandlerdialog-client-id"

val testAzureAppPreAuthorizedApps = listOf(
    PreAuthorizedClient(
        name = "dev-gcp:teamsykefravr:isbehandlerdialog",
        clientId = testIsBehandlerDialogClientId,
    ),
)
