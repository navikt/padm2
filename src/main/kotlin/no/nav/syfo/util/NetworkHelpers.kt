package no.nav.syfo.util

import no.nav.syfo.metrics.NETWORK_CALL_SUMMARY
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.network-helpers")

suspend inline fun <reified T> timed(callName: String, crossinline block: suspend() -> T) = NETWORK_CALL_SUMMARY.labels(callName).startTimer().use {
    block()
}

fun isCausedBy(throwable: Throwable, depth: Int, legalExceptions: Array<out KClass<out Throwable>>): Boolean {
    var current: Throwable = throwable
    for (i in 0.until(depth)) {
        if (legalExceptions.any { it.isInstance(current) }) {
            return true
        }
        current = current.cause ?: break
    }
    return false
}
