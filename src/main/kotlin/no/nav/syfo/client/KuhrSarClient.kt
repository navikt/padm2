package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.KtorExperimentalAPI
import java.util.Date
import kotlin.math.max
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.SamhandlerPraksisType
import no.nav.syfo.util.LoggingMeta
import org.apache.commons.text.similarity.LevenshteinDistance
import java.io.IOException

@KtorExperimentalAPI
class SarClient(
    private val endpointUrl: String,
    private val httpClient: HttpClient
) {
    suspend fun getSamhandler(ident: String): List<Samhandler> = retry("get_samhandler") {
        val response: HttpResponse = httpClient.get("$endpointUrl/rest/sar/samh") {
            accept(ContentType.Application.Json)
            parameter("ident", ident)
        }
        when(response.status) {
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
    herId: String?,
    loggingMeta: LoggingMeta
): SamhandlerPraksisMatch? {
    // Finn samhandlere med status aktiv.
    val aktiveSamhandlere = samhandlere.flatMap { it.samh_praksis }
        .filter { praksis -> praksis.samh_praksis_status_kode == "aktiv" }

    // Hvis vi ikke finner noen aktive samhandlere, logger vi det.
    // Hvorfor kan vi ikke prøve match på inaktive her?
    if (aktiveSamhandlere.isEmpty()) {
        log.info("Fant ingen aktive samhandlere. {}  Meta: {}, {} ",
            keyValue("praksis Informasjo", samhandlere.formaterPraksis()),
            keyValue("antall praksiser", samhandlere.size),
            StructuredArguments.fields(loggingMeta)
        )
    }

    // Hvis man har fått med en herID i dialogmeldingen, og man har minst én aktiv samhandler
    // Hvis man finner en samhandler med samme herID som den vi fikk i meldingen, er det god stemning. Da vet vi at vi har riktig samhandler!
    if (!herId.isNullOrEmpty() && aktiveSamhandlere.isNotEmpty()) {
        val samhandlerByHerId = aktiveSamhandlere.find {
            it.her_id == herId
        }
        if (samhandlerByHerId != null) {
            log.info("Fant samhandler basert på herid. herid: $herId, {}, {}",
                keyValue("praksis Informasjo", samhandlere.formaterPraksis()),
                StructuredArguments.fields(loggingMeta)
            )
            return SamhandlerPraksisMatch(samhandlerByHerId, 100.0)
        }
    }

    // Finn aktive samhandlere som også har navn.
    // Det ser ut til å gjelde de aller fleste samhandlere.
    val aktiveSamhandlereMedNavn = samhandlere.flatMap { it.samh_praksis }
        .filter { praksis -> praksis.samh_praksis_status_kode == "aktiv" }
        .filter { !it.navn.isNullOrEmpty() }

    // IKke herID
    // Må være minst én aktiv samhandler, men ingen aktive som har navn (hvis ingen aktive, bryr vi oss ikke om navn)
    // Dette skjer nesten aldri
    // Hvis man finner en samhandler (aktiv, uten navn) som er av typen FALE/FALO, returnerer vi 999% match. Det fører til ekstra logging i blockingApplicationRunner
    // Hvis ingen her er FALE/FALO, kommer vi ut av ifen, og kommer til 'return aktiveSamhandlereMedNavn' (ca. linje 185 med kommentarer)
    // Hvorfor bryr vi oss om FALE/FALO her??! Det er bare mer logikk, og det fører til en ekstra logging, men resultatet blir det samme: At vi ikke oppdaterer e-mottak. (Vi finner uansett bare den første FALE/FALO-samhandleren, det kan jo være flere her?)
    if (aktiveSamhandlereMedNavn.isNullOrEmpty() && !aktiveSamhandlere.isNullOrEmpty()) {
        val samhandlerFALEOrFALO = aktiveSamhandlere.find {
            it.samh_praksis_type_kode == SamhandlerPraksisType.FASTLEGE.kodeVerdi ||
                    it.samh_praksis_type_kode == SamhandlerPraksisType.FASTLONNET.kodeVerdi
        }
        if (samhandlerFALEOrFALO != null) {
            return SamhandlerPraksisMatch(samhandlerFALEOrFALO, 999.0)
        }
        // Hvis vi ikke har funnet noen aktive samhandlere, prøver vi å finne match på inaktive samhandlere.
        // Man prøver å matche på inaktive som også har navn.
        // Vi gjør en lignende match lenger opp, der man kun logger
        // Hvorfor kommer denne sist? Hvis denne er true, vil jo aldri 'aktiveSamhandlereMedNavn' ha elementer? 'aktivMedNavn' er jo et subset av 'aktive'.
    } else if (aktiveSamhandlere.isNullOrEmpty()) {
        val inaktiveSamhandlerMatchingPaaOrganisjonsNavn = samhandlerMatchingPaaOrganisjonsNavn(samhandlere, orgName)
        return filtererBortSamhanlderPraksiserPaaProsentMatch(
            inaktiveSamhandlerMatchingPaaOrganisjonsNavn,
            70.0,
            orgName,
            loggingMeta
        )
    }

    // Hvis ikke noe annet matcher, havner man her
    // Her må man ha minst én aktiv samhandler med navn, og ingen som har herID
    // Her er det en metode som prøver å finne best mulig match mellom navnet på samhandler og orgnavn vi fikk med i dialogmeldingen.
    // Returner den samhandleren som fikk best match på navn.
    // Hvis man har minst én aktiv samhandler, men ingen med navn, og ingen av typen FALE/FALO, havner man her, men da har man tom liste, da returneres 'null' fra map-metoden.
    return aktiveSamhandlereMedNavn
        .map { samhandlerPraksis ->
            SamhandlerPraksisMatch(samhandlerPraksis, calculatePercentageStringMatch(samhandlerPraksis.navn, orgName) * 100)
        }.maxBy { it.percentageMatch }
}

fun samhandlerMatchingPaaOrganisjonsNavn(samhandlere: List<Samhandler>, orgName: String): SamhandlerPraksisMatch? {
    val inaktiveSamhandlereMedNavn = samhandlere.flatMap { it.samh_praksis }
        .filter { samhandlerPraksis -> samhandlerPraksis.samh_praksis_status_kode == "inaktiv" }
        .filter { samhandlerPraksis -> !samhandlerPraksis.navn.isNullOrEmpty() }
    return if (!inaktiveSamhandlereMedNavn.isNullOrEmpty()) {
        inaktiveSamhandlereMedNavn
            .map { samhandlerPraksis ->
                SamhandlerPraksisMatch(samhandlerPraksis, calculatePercentageStringMatch(samhandlerPraksis.navn?.toLowerCase(), orgName.toLowerCase()) * 100)
            }.maxBy { it.percentageMatch }
    } else {
        null
    }
}

fun filtererBortSamhanlderPraksiserPaaProsentMatch(
    samhandlerPraksis: SamhandlerPraksisMatch?,
    prosentMatch: Double,
    orgName: String,
    loggingMeta: LoggingMeta
): SamhandlerPraksisMatch? {
    return if (samhandlerPraksis != null && samhandlerPraksis.percentageMatch >= prosentMatch) {
        log.info("Beste match ble samhandler praksis: " +
                "Orgnumer: ${samhandlerPraksis.samhandlerPraksis.org_id} " +
                "Navn: ${samhandlerPraksis.samhandlerPraksis.navn} " +
                "Tssid: ${samhandlerPraksis.samhandlerPraksis.tss_ident} " +
                "Adresselinje1: ${samhandlerPraksis.samhandlerPraksis.arbeids_adresse_linje_1} " +
                "Samhandler praksis type: ${samhandlerPraksis.samhandlerPraksis.samh_praksis_type_kode} " +
                "Prosent match:${samhandlerPraksis.percentageMatch} %, basert på dialogmeldingen organisjons navn: $orgName " +
                "{}", StructuredArguments.fields(loggingMeta)
        )
        samhandlerPraksis
    } else {
        null
    }
}
