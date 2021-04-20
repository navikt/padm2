package no.nav.syfo.util

enum class LogType {
    INVALID_MESSAGE,
    TEST_FNR_IN_PRODUCTION
}

fun createLogEntry(
    type: LogType,
    loggingMeta: LoggingMeta
): Map<String, String?> {
    return mapOf(
        "type" to type.name,
        "mottakId" to loggingMeta.mottakId,
        "orgNr" to loggingMeta.orgNr,
        "msgId" to loggingMeta.msgId
    )
}

fun createLogEntry(
    type: LogType,
    loggingMeta: LoggingMeta,
    vararg arguments: Pair<String, String>
): Map<String, String?> {
    return mapOf(
        "type" to type.name,
        "mottakId" to loggingMeta.mottakId,
        "orgNr" to loggingMeta.orgNr,
        "msgId" to loggingMeta.msgId
    ).plus(arguments)
}

data class LoggingMeta(
    val mottakId: String,
    val orgNr: String?,
    val msgId: String,
    val dialogmeldingId: String = ""
)

class TrackableException(override val cause: Throwable) : RuntimeException()

suspend fun <O> wrapExceptions(block: suspend () -> O): O {
    try {
        return block()
    } catch (e: Exception) {
        throw TrackableException(e)
    }
}
