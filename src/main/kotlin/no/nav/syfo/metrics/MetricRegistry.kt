package no.nav.syfo.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import io.prometheus.client.Summary

const val METRICS_NS = "padm2"

val REQUEST_TIME: Summary = Summary.build()
    .namespace(METRICS_NS)
    .name("request_time_ms")
    .help("Request time in milliseconds.").register()

val INCOMING_MESSAGE_COUNTER: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("incoming_message_count")
    .help("Counts the number of incoming messages")
    .register()

val APPREC_COUNTER: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("apprec_count")
    .help("Counts the number of apprec messages")
    .register()

val INVALID_MESSAGE_NO_NOTICE: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("invalid_message_no_notice_count")
    .help("Counts the number of messages, that has not enough information to be sendt to the rule engine ")
    .register()

val TEST_FNR_IN_PROD: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("test_fnr_in_prod")
    .help("Counts the number of messages that contains a test fnr i prod")
    .register()

val MELDING_LAGER_I_JOARK: Counter = Counter.Builder()
    .namespace(METRICS_NS)
    .name("melding_lagret_i_joark")
    .help("Meldinger som er lagret i joark")
    .register()

val CASES_CREATED: Counter = Counter.Builder()
    .namespace(METRICS_NS)
    .name("sak_lagret")
    .help("Antall saker som opprettes")
    .register()

val MESSAGE_STORED_IN_DB_COUNTER: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("message_stored_in_db_count")
    .help("Counts the number of messages stored in db")
    .register()

val MESSAGES_SENT_TO_BOQ: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("message_sent_to_boq")
    .help("Counts the number of messages sent to backout queue")
    .register()

val RULE_HIT_COUNTER: Counter = Counter.Builder()
    .namespace(METRICS_NS)
    .name("rule_hit_counter")
    .labelNames("rule_name")
    .help("Registers a counter for each rule in the rule set")
    .register()

val RULE_HIT_STATUS_COUNTER: Counter = Counter.Builder()
    .namespace(METRICS_NS)
    .name("rule_hit_status_counter")
    .labelNames("rule_status")
    .help("Registers a counter for each rule status")
    .register()
