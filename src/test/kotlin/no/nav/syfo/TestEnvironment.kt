package no.nav.syfo

import no.nav.syfo.application.ApplicationState
import java.net.ServerSocket
import java.util.*

fun testEnvironment(
    kafkaBootstrapServers: String,
    azureTokenEndpoint: String = "azureTokenEndpoint",
) = Environment(
    aadAppClient = "isdialogmote-client-id",
    aadAppSecret = "isdialogmote-secret",
    aadTokenEndpoint = azureTokenEndpoint,
    kafka = ApplicationEnvironmentKafka(
        bootstrapServers = kafkaBootstrapServers,
        aivenCredstorePassword = "credstorepassord",
        aivenTruststoreLocation = "truststore",
        aivenKeystoreLocation = "keystore",
    ),
    serviceuserUsername = "user",
    serviceuserPassword = "password",
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
