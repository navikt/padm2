package no.nav.syfo.util


import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.util.pipeline.PipelineContext
import net.logstash.logback.argument.StructuredArguments

const val TEMA_HEADER = "Tema"
const val ALLE_TEMA_HEADERVERDI = "GEN"

fun bearerHeader(token: String): String {
    return "Bearer $token"
}

const val NAV_CALL_ID_HEADER = "Nav-Call-Id"
fun PipelineContext<out Unit, ApplicationCall>.getCallId(): String {
    return this.call.request.headers[NAV_CALL_ID_HEADER].toString()
}
fun callIdArgument(callId: String) = StructuredArguments.keyValue("callId", callId)!!

const val NAV_CONSUMER_ID_HEADER = "Nav-Consumer-Id"
