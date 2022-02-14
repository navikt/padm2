package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.util.Date
import kotlin.math.max
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.logger
import no.nav.syfo.model.SamhandlerPraksisType
import org.apache.commons.text.similarity.LevenshteinDistance
import java.io.IOException
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.bearerHeader
import no.nav.syfo.util.retry

class KuhrSarClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val kuhrSarClientId: String,
    private val kuhrSarUrl: String,
    private val httpClient: HttpClient
) {
    suspend fun getSamhandler(ident: String): List<Samhandler> = retry("get_samhandler") {
        val token = azureAdV2Client.getSystemToken(kuhrSarClientId)
            ?: throw RuntimeException("Failed to send request to KuhrSar: No token was found")
        val response: HttpResponse = httpClient.get("$kuhrSarUrl/sar/rest/v2/samh") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
            parameter("ident", ident)
        }
        when (response.status) {
            HttpStatusCode.OK -> response.receive()
            else -> throw IOException("Vi fikk en uventet feil fra kuhrSar, prøver på nytt! ${response.content}")
        }
    }
}

data class Samhandler(
    val samh_id: String,
    val navn: String,
    val samh_type_kode: String,
    val behandling_utfall_kode: String,
    val unntatt_veiledning: String,
    val godkjent_manuell_krav: String,
    val ikke_godkjent_for_refusjon: String,
    val godkjent_egenandel_refusjon: String,
    val godkjent_for_fil: String,
    val endringslogg_tidspunkt_siste: Date?,
    val samh_praksis: List<SamhandlerPraksis>
)

data class SamhandlerPraksis(
    val org_id: String?,
    val refusjon_type_kode: String?,
    val laerer: String?,
    val lege_i_spesialisering: String?,
    val tidspunkt_resync_periode: Date?,
    val tidspunkt_registrert: Date?,
    val samh_praksis_status_kode: String,
    val telefonnr: String?,
    val arbeids_kommune_nr: String?,
    val arbeids_postnr: String?,
    val arbeids_adresse_linje_1: String?,
    val arbeids_adresse_linje_2: String?,
    val arbeids_adresse_linje_3: String?,
    val arbeids_adresse_linje_4: String?,
    val arbeids_adresse_linje_5: String?,
    val her_id: String?,
    val post_adresse_linje_1: String?,
    val post_adresse_linje_2: String?,
    val post_adresse_linje_3: String?,
    val post_adresse_linje_4: String?,
    val post_adresse_linje_5: String?,
    val post_kommune_nr: String?,
    val post_postnr: String?,
    val tss_ident: String,
    val navn: String?,
    val ident: String?,
    val samh_praksis_type_kode: String?,
    val samh_id: String,
    val samh_praksis_id: String,
    val samh_praksis_periode: List<SamhandlerPeriode>
)

data class SamhandlerPeriode(
    val slettet: String,
    val gyldig_fra: Date,
    val gyldig_til: Date?,
    val samh_praksis_id: String,
    val samh_praksis_periode_id: String
)

data class SamhandlerPraksisMatch(val samhandlerPraksis: SamhandlerPraksis, val percentageMatch: Double)

fun calculatePercentageStringMatch(str1: String?, str2: String): Double {
    val maxDistance = max(str1?.length!!, str2.length).toDouble()
    val distance = LevenshteinDistance().apply(str2, str1).toDouble()
    return (maxDistance - distance) / maxDistance
}

fun List<SamhandlerPeriode>.formaterPerioder() = joinToString(",", "periode(", ") ") { periode ->
    "${periode.gyldig_fra} -> ${periode.gyldig_til}"
}

fun List<Samhandler>.formaterPraksis() = flatMap { it.samh_praksis }
    .joinToString(",", "praksis(", ") ") { praksis ->
        "${praksis.navn}: ${praksis.samh_praksis_status_kode} ${praksis.samh_praksis_periode.formaterPerioder()}"
    }

fun findBestSamhandlerPraksis(
    samhandlere: List<Samhandler>,
    orgName: String,
    legekontorHerId: String?,
    loggingMeta: LoggingMeta
): SamhandlerPraksisMatch? {
    val aktiveSamhandlere = samhandlere.flatMap { it.samh_praksis }
        .filter { praksis -> praksis.samh_praksis_status_kode == "aktiv" }

    if (aktiveSamhandlere.isEmpty()) {
        logger.info(
            "Fant ingen aktive samhandlere. {}  Meta: {}, {} ",
            keyValue("praksis Informasjo", samhandlere.formaterPraksis()),
            keyValue("antall praksiser", samhandlere.size),
            StructuredArguments.fields(loggingMeta)
        )
    }

    val samhandlerPraksisByHerId = getSamhandlerPraksisByHerId(legekontorHerId, aktiveSamhandlere)
    if (samhandlerPraksisByHerId != null) {
        logger.info(
            "Fant samhandler basert på herid. herid: $legekontorHerId, {}, {}",
            keyValue("praksisinformasjon", samhandlere.formaterPraksis()),
            StructuredArguments.fields(loggingMeta)
        )
        return SamhandlerPraksisMatch(samhandlerPraksisByHerId, 100.0)
    }

    val aktiveSamhandlereMedNavn = samhandlere.flatMap { it.samh_praksis }
        .filter { praksis -> praksis.samh_praksis_status_kode == "aktiv" }
        .filter { !it.navn.isNullOrEmpty() }

    if (erAlleAktiveSamhandlereUtenNavn(aktiveSamhandlereMedNavn, aktiveSamhandlere)) {
        val samhandlerFALEOrFALO = aktiveSamhandlere.find {
            it.samh_praksis_type_kode == SamhandlerPraksisType.FASTLEGE.kodeVerdi ||
                it.samh_praksis_type_kode == SamhandlerPraksisType.FASTLONNET.kodeVerdi
        }
        if (samhandlerFALEOrFALO != null) {
            return SamhandlerPraksisMatch(samhandlerFALEOrFALO, 999.0)
        }
    } else if (aktiveSamhandlere.isNullOrEmpty()) {
        val samhandlerPraksisByOrgName = getInactiveSamhandlerPraksisByOrgName(samhandlere, orgName)
        return samhandlerPraksisMatchTest(
            samhandlerPraksisByOrgName,
            70.0,
            orgName,
            loggingMeta
        )
    }

    return getSamhandlerPraksisByOrgName(aktiveSamhandlereMedNavn, orgName, loggingMeta)
}

fun getSamhandlerPraksisByHerId(legekontorHerId: String?, aktiveSamhandlere: List<SamhandlerPraksis>): SamhandlerPraksis? {
    val hasLegekontorHerID = !legekontorHerId.isNullOrEmpty()
    val hasAktivSamhandler = aktiveSamhandlere.isNotEmpty()

    return if (hasLegekontorHerID && hasAktivSamhandler) {
        aktiveSamhandlere.find {
            it.her_id == legekontorHerId
        }
    } else {
        null
    }
}

fun getSamhandlerPraksisByOrgName(aktivePraksiserWithOrgName: List<SamhandlerPraksis>, orgName: String, loggingMeta: LoggingMeta): SamhandlerPraksisMatch? {
    val praksisWithMostSimilarOrgName = aktivePraksiserWithOrgName
        .map { samhandlerPraksis ->
            SamhandlerPraksisMatch(samhandlerPraksis, calculatePercentageStringMatch(samhandlerPraksis.navn, orgName) * 100)
        }.maxByOrNull { it.percentageMatch }
    return samhandlerPraksisMatchTest(
        praksisWithMostSimilarOrgName,
        70.0,
        orgName,
        loggingMeta
    )
}

private fun erAlleAktiveSamhandlereUtenNavn(
    aktiveSamhandlereMedNavn: List<SamhandlerPraksis>,
    aktiveSamhandlere: List<SamhandlerPraksis>
) = aktiveSamhandlereMedNavn.isNullOrEmpty() && !aktiveSamhandlere.isNullOrEmpty()

fun getInactiveSamhandlerPraksisByOrgName(samhandlere: List<Samhandler>, orgName: String): SamhandlerPraksisMatch? {
    val inaktiveSamhandlereMedNavn = samhandlere.flatMap { it.samh_praksis }
        .filter { samhandlerPraksis -> samhandlerPraksis.samh_praksis_status_kode == "inaktiv" }
        .filter { samhandlerPraksis -> !samhandlerPraksis.navn.isNullOrEmpty() }
    return if (!inaktiveSamhandlereMedNavn.isNullOrEmpty()) {
        inaktiveSamhandlereMedNavn
            .map { samhandlerPraksis ->
                SamhandlerPraksisMatch(samhandlerPraksis, calculatePercentageStringMatch(samhandlerPraksis.navn?.toLowerCase(), orgName.toLowerCase()) * 100)
            }.maxByOrNull { it.percentageMatch }
    } else {
        null
    }
}

fun samhandlerPraksisMatchTest(
    samhandlerPraksis: SamhandlerPraksisMatch?,
    percentageMatchLimit: Double,
    orgName: String,
    loggingMeta: LoggingMeta
): SamhandlerPraksisMatch? {
    return if (samhandlerPraksis != null && samhandlerPraksis.percentageMatch >= percentageMatchLimit) {
        logger.info(
            "Beste match ble samhandler praksis: " +
                "Orgnumer: ${samhandlerPraksis.samhandlerPraksis.org_id} " +
                "Navn: ${samhandlerPraksis.samhandlerPraksis.navn} " +
                "Tssid: ${samhandlerPraksis.samhandlerPraksis.tss_ident} " +
                "Adresselinje1: ${samhandlerPraksis.samhandlerPraksis.arbeids_adresse_linje_1} " +
                "Samhandler praksis type: ${samhandlerPraksis.samhandlerPraksis.samh_praksis_type_kode} " +
                "Prosent match:${samhandlerPraksis.percentageMatch} %, basert på dialogmeldingen organisjons navn: $orgName " +
                "{}",
            StructuredArguments.fields(loggingMeta)
        )
        samhandlerPraksis
    } else {
        null
    }
}
