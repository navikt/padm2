package no.nav.syfo.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

const val METRICS_NS = "padm2"

val METRICS_REGISTRY = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

val REQUEST_TIME: Timer = Timer
    .builder("${METRICS_NS}_request_time_ms")
    .description("Request time in milliseconds.")
    .register(METRICS_REGISTRY)

val INCOMING_MESSAGE_COUNTER: Counter = Counter
    .builder("${METRICS_NS}_incoming_message_count")
    .description("Counts the number of incoming messages")
    .register(METRICS_REGISTRY)

val APPREC_COUNTER: Counter = Counter
    .builder("${METRICS_NS}_apprec_count")
    .description("Counts the number of apprec messages")
    .register(METRICS_REGISTRY)

val INVALID_MESSAGE_NO_NOTICE: Counter = Counter
    .builder("${METRICS_NS}_invalid_message_no_notice_count")
    .description("Counts the number of messages, that has not enough information to be sendt to the rule engine ")
    .register(METRICS_REGISTRY)

val INVALID_PDF_VEDLEGG: Counter = Counter
    .builder("${METRICS_NS}_invalid_pdf_vedlegg")
    .description("Counts the number of invalid pdf vedlegg")
    .register(METRICS_REGISTRY)

val TEST_FNR_IN_PROD: Counter = Counter
    .builder("${METRICS_NS}_test_fnr_in_prod")
    .description("Counts the number of messages that contains a test fnr i prod")
    .register(METRICS_REGISTRY)

val MELDING_LAGER_I_JOARK: Counter = Counter
    .builder("${METRICS_NS}_melding_lagret_i_joark")
    .description("Meldinger som er lagret i joark")
    .register(METRICS_REGISTRY)

val MESSAGE_STORED_IN_DB_COUNTER: Counter = Counter
    .builder("${METRICS_NS}_message_stored_in_db_count")
    .description("Counts the number of messages stored in db")
    .register(METRICS_REGISTRY)

val MESSAGES_SENT_TO_BOQ: Counter = Counter
    .builder("${METRICS_NS}_message_sent_to_boq")
    .description("Counts the number of messages sent to backout queue")
    .register(METRICS_REGISTRY)

val MESSAGES_STILL_FAIL_AFTER_1H: Counter = Counter
    .builder("${METRICS_NS}_messages_still_fail_after_1h")
    .description("Counts the number of messages that still fails after 1 hour")
    .register(METRICS_REGISTRY)

val RULE_HIT_COUNTER: Counter = Counter
    .builder("${METRICS_NS}_rule_hit_counter")
    .description("Registers a counter for each rule in the rule set")
    .register(METRICS_REGISTRY)

val TSS_MISS_COUNTER: Counter = Counter
    .builder("${METRICS_NS}_TSS_MISS_COUNTER")
    .description("Counts the number of times we can't find a TSS-ident")
    .register(METRICS_REGISTRY)

val SANITIZE_INVALID_CHAR_COUNTER: Counter = Counter
    .builder("${METRICS_NS}_SANITIZE_INVALID_CHAR_COUNTER")
    .description("Counts the number of encountered illegal chars from sanitation")
    .register(METRICS_REGISTRY)

val DONT_UPDATE_EMOTTAK_MISSING_PARTNERREF: Counter = Counter
    .builder("${METRICS_NS}_dont_update_emottak_missing_partnerref")
    .description("Counts the number of missing partnerref leading to not updating emottak")
    .register(METRICS_REGISTRY)
