package no.nav.syfo

data class Environment(
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "padm-2"),
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt()
)

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
