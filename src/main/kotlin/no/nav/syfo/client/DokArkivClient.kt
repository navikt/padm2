package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayList
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.AvsenderMottaker
import no.nav.syfo.model.Bruker
import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.model.Dokument
import no.nav.syfo.model.Dokumentvarianter
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse
import no.nav.syfo.model.Sak
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.objectMapper
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.validation.validatePersonAndDNumber

@KtorExperimentalAPI
class DokArkivClient(
    private val url: String,
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun createJournalpost(
        journalpostRequest: JournalpostRequest,
        loggingMeta: LoggingMeta
    ): JournalpostResponse = retry(
        callName = "dokarkiv",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L)
    ) {
        try {
            log.info(
                "Kall til dokakriv Nav-Callid {}, {}", journalpostRequest.eksternReferanseId,
                fields(loggingMeta)
            )
            httpClient.post<JournalpostResponse>(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${stsClient.oidcToken().access_token}")
                header("Nav-Callid", journalpostRequest.eksternReferanseId)
                body = journalpostRequest
                parameter("forsoekFerdigstill", true)
            }
        } catch (e: Exception) {
            log.warn("Oppretting av journalpost feilet: ${e.message}, {}", fields(loggingMeta))
            throw e
        }
    }
}

fun createJournalpostPayload(
    dialogmelding: Dialogmelding,
    caseId: String,
    pdf: ByteArray,
    avsenderFnr: String,
    ediLoggId: String,
    signaturDato: LocalDateTime,
    validationResult: ValidationResult,
    pasientFnr: String,
    vedleggListe: List<Vedlegg>?
) = JournalpostRequest(
    avsenderMottaker = when (validatePersonAndDNumber(avsenderFnr)) {
        true -> createAvsenderMottakerValidFnr(avsenderFnr, dialogmelding)
        else -> createAvsenderMottakerNotValidFnr(dialogmelding)
    },
    bruker = Bruker(
        id = pasientFnr,
        idType = "FNR"
    ),
    dokumenter = leggtilDokument(ediLoggId, dialogmelding, pdf, validationResult, signaturDato, vedleggListe),
    eksternReferanseId = ediLoggId,
    journalfoerendeEnhet = "9999",
    journalpostType = "INNGAAENDE",
    kanal = "HELSENETTET",
    sak = Sak(
        arkivsaksnummer = caseId,
        arkivsaksystem = "GSAK"
    ),
    tema = "OPP",
    tittel = createTittleJournalpost(validationResult, signaturDato)
)

fun leggtilDokument(
    ediLoggId: String,
    dialogmelding: Dialogmelding,
    pdf: ByteArray,
    validationResult: ValidationResult,
    signaturDato: LocalDateTime,
    vedleggListe: List<Vedlegg>?
): List<Dokument> {
    val listDokument = ArrayList<Dokument>()
    listDokument.add(
        Dokument(
            dokumentvarianter = listOf(
                Dokumentvarianter(
                    filnavn = "$ediLoggId.pdf",
                    filtype = "PDFA",
                    variantformat = "ARKIV",
                    fysiskDokument = pdf
                ),
                Dokumentvarianter(
                    filnavn = "Dialogmelding Original",
                    filtype = "JSON",
                    variantformat = "ORIGINAL",
                    fysiskDokument = objectMapper.writeValueAsBytes(dialogmelding)
                )
            ),
            tittel = createTittleJournalpost(validationResult, signaturDato)
        )
    )
    if (!vedleggListe.isNullOrEmpty()) {
        val listVedleggDokumenter = ArrayList<Dokument>()
        vedleggListe.map {
            listVedleggDokumenter.add(
                Dokument(
                    dokumentvarianter = listOf(
                        Dokumentvarianter(
                            filtype = findFiltype(it),
                            filnavn = when (it.beskrivelse.length >= 200) {
                                true -> "${it.beskrivelse.substring(0, 199)}.${findFiltype(it).toLowerCase()}"
                                else -> "${it.beskrivelse}.${findFiltype(it).toLowerCase()}"
                            },
                            variantformat = when (it.mimeType == "application/pdf") {
                                true -> "ARKIV"
                                else -> "ORIGINAL"
                            },
                            fysiskDokument = it.contentBase64
                        )
                    ),
                    tittel = "Vedlegg til dialogmelding"
                )
            )
        }

        listVedleggDokumenter.map { vedlegg ->
            listDokument.add(vedlegg)
        }
    }

    return listDokument
}

fun findFiltype(vedlegg: Vedlegg): String =
    when (vedlegg.mimeType) {
        "application/pdf" -> "PDFA"
        "image/tiff" -> "TIFF"
        "image/png" -> "PNG"
        "image/jpeg" -> "JPEG"
        else -> throw RuntimeException("Vedlegget er av av ukjent mimeType ${vedlegg.mimeType}")
    }

fun createAvsenderMottakerValidFnr(avsenderFnr: String, dialogmelding: Dialogmelding):
        AvsenderMottaker = AvsenderMottaker(
    id = avsenderFnr,
    idType = "FNR",
    land = "Norge",
    navn = dialogmelding.navnHelsepersonell
)

fun createAvsenderMottakerNotValidFnr(dialogmelding: Dialogmelding): AvsenderMottaker = AvsenderMottaker(
    land = "Norge",
    navn = dialogmelding.navnHelsepersonell
)

fun createTittleJournalpost(validationResult: ValidationResult, signaturDato: LocalDateTime): String {
    return if (validationResult.status == Status.INVALID) {
        "Avvist Dialogmelding ${formaterDato(signaturDato)}"
    } else {
        "Dialogmelding ${formaterDato(signaturDato)}"
    }
}

fun formaterDato(dato: LocalDateTime): String {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    return dato.format(formatter)
}
