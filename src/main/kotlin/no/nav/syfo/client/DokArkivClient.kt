package no.nav.syfo.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.logger
import no.nav.syfo.model.*
import no.nav.syfo.util.*
import no.nav.syfo.validation.validatePersonAndDNumber
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class DokArkivClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val dokArkivClientId: String,
    private val url: String,
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
            logger.info("Kall til dokarkiv Nav-Callid {}, {}", journalpostRequest.eksternReferanseId, fields(loggingMeta))

            val accessToken = azureAdV2Client.getSystemToken(dokArkivClientId)?.accessToken
                ?: throw RuntimeException("Failed to send request to DokArkiv: No token was found")

            val response: HttpResponse = httpClient.post(url) {
                header("Authorization", "Bearer $accessToken")
                header("Nav-Callid", journalpostRequest.eksternReferanseId)
                setBody(journalpostRequest)
                contentType(ContentType.Application.Json)
                parameter("forsoekFerdigstill", true)
            }
            when (response.status) {
                HttpStatusCode.OK -> response.body()
                HttpStatusCode.Created -> response.body()
                else -> throw RuntimeException("Http status: ${response.status} Content: ${response.bodyAsText()}")
            }
        } catch (e: Exception) {
            logger.warn("Oppretting av journalpost feilet: ${e.message}, {}", fields(loggingMeta))
            throw e
        }
    }
}

fun createJournalpostPayload(
    dialogmelding: Dialogmelding,
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
        idType = IdType.PERSON_IDENT.value
    ),
    dokumenter = leggtilDokument(ediLoggId, dialogmelding, pdf, validationResult, signaturDato, vedleggListe),
    eksternReferanseId = ediLoggId,
    journalfoerendeEnhet = "9999",
    journalpostType = JournalpostType.INNGAAENDE.value,
    kanal = "HELSENETTET",
    sak = Sak(
        sakstype = SaksType.GENERELL.value,
    ),
    tema = "OPP",
    tittel = createTitleJournalpost(validationResult, signaturDato),
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

    logger.info("Converting vedlegg of type ${vedlegg.mimeType} to PDFA")

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
        "image/jpg" -> "JPEG"
        else -> throw RuntimeException("Vedlegget er av ukjent mimeType ${vedlegg.mimeType}")
    }

fun createAvsenderMottakerValidFnr(
    avsenderFnr: String,
    dialogmelding: Dialogmelding
): AvsenderMottaker = AvsenderMottaker(
    id = avsenderFnr,
    idType = IdType.PERSON_IDENT.value,
    navn = dialogmelding.navnHelsepersonell,
)

fun createAvsenderMottakerNotValidFnr(
    dialogmelding: Dialogmelding
): AvsenderMottaker = AvsenderMottaker(
    navn = dialogmelding.navnHelsepersonell,
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
