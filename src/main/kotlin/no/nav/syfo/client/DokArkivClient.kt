package no.nav.syfo.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.util.retry
import no.nav.syfo.log
import no.nav.syfo.model.*
import no.nav.syfo.objectMapper
import no.nav.syfo.util.ImageToPDF
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.validation.validatePersonAndDNumber
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

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
            log.info("Kall til dokarkiv Nav-Callid {}, {}", journalpostRequest.eksternReferanseId, fields(loggingMeta))

            val response: HttpResponse = httpClient.post(url) {
                header("Authorization", "Bearer ${stsClient.oidcToken().access_token}")
                header("Nav-Callid", journalpostRequest.eksternReferanseId)
                body = journalpostRequest
                contentType(ContentType.Application.Json)
                parameter("forsoekFerdigstill", true)
            }
            when (response.status) {
                HttpStatusCode.OK -> response.receive()
                HttpStatusCode.Created -> response.receive()
                else -> throw RuntimeException("Http status: ${response.status} Content: ${response.content}")
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
    tittel = createTitleJournalpost(validationResult, signaturDato)
)

fun leggtilDokument(
    ediLoggId: String,
    dialogmelding: Dialogmelding,
    dialogmeldingPDF: ByteArray,
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
                    fysiskDokument = dialogmeldingPDF
                ),
                Dokumentvarianter(
                    filnavn = "Dialogmelding Original",
                    filtype = "JSON",
                    variantformat = "ORIGINAL",
                    fysiskDokument = objectMapper.writeValueAsBytes(dialogmelding)
                )
            ),
            tittel = createTitleJournalpost(validationResult, signaturDato)
        )
    )
    if (!vedleggListe.isNullOrEmpty()) {
        val listVedleggDokumenter = ArrayList<Dokument>()
        vedleggListe
            .filter { vedlegg -> vedlegg.contentBase64.isNotEmpty() }
            .map { vedlegg -> vedleggToPDF(vedlegg) }
            .mapIndexed { index, vedlegg ->
                listVedleggDokumenter.add(
                    Dokument(
                        dokumentvarianter = listOf(
                            Dokumentvarianter(
                                filtype = findFiltype(vedlegg),
                                filnavn = "Vedlegg_nr_${index}_Dialogmelding_$ediLoggId",
                                variantformat = "ARKIV",
                                fysiskDokument = vedlegg.contentBase64
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

fun vedleggToPDF(vedlegg: Vedlegg): Vedlegg {
    if (findFiltype(vedlegg) == "PDFA") return vedlegg

    log.info("Converting vedlegg of type ${vedlegg.mimeType} to PDFA")

    val image =
        ByteArrayOutputStream().use { outputStream ->
            ImageToPDF(vedlegg.contentBase64.inputStream(), outputStream)
            outputStream.toByteArray()
        }

    return Vedlegg(
        "application/pdf",
        vedlegg.beskrivelse,
        image
    )
}

fun findFiltype(vedlegg: Vedlegg): String =
    when (vedlegg.mimeType) {
        "application/pdf" -> "PDFA"
        "image/tiff" -> "TIFF"
        "image/png" -> "PNG"
        "image/jpeg" -> "JPEG"
        else -> throw RuntimeException("Vedlegget er av av ukjent mimeType ${vedlegg.mimeType}")
    }

fun createAvsenderMottakerValidFnr(
    avsenderFnr: String,
    dialogmelding: Dialogmelding
): AvsenderMottaker = AvsenderMottaker(
    id = avsenderFnr,
    idType = "FNR",
    land = "Norge",
    navn = dialogmelding.navnHelsepersonell
)

fun createAvsenderMottakerNotValidFnr(
    dialogmelding: Dialogmelding
): AvsenderMottaker = AvsenderMottaker(
    land = "Norge",
    navn = dialogmelding.navnHelsepersonell
)

fun createTitleJournalpost(
    validationResult: ValidationResult,
    signaturDato: LocalDateTime
): String {
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
