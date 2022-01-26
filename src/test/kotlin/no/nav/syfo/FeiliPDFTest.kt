package no.nav.syfo

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.config
import no.nav.syfo.client.createPdfPayload
import no.nav.syfo.model.*
import no.nav.syfo.util.*
import org.junit.Test
import java.io.File
import java.io.StringReader
import java.nio.file.Paths

class FeiliPDFTest {
    @Test
    internal fun `Create pdf`() {
        val fellesformat = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/feilpdf/633889387.xml"))
        ) as XMLEIFellesformat

        val pasientNavn = extractPasientNavn(fellesformat)
        val vedlegg = extractVedlegg(fellesformat)
        val vedleggListe = vedlegg.map { it.toVedlegg() }
        val antallVedlegg = vedleggListe?.size ?: 0

        val validationResult = ValidationResult(
            status = Status.OK,
            ruleHits = emptyList(),
        )
        val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
        val dialogmeldingType = findDialogmeldingType(receiverBlock.ebService, receiverBlock.ebAction)
        val legeIdent = receiverBlock.avsenderFnrFraDigSignatur
        val dialogmeldingXml = extractDialogmelding(fellesformat)
        val msgHead: XMLMsgHead = fellesformat.get()
        val behandlerNavn = extractBehandlerNavn(fellesformat)
        val innbyggerIdent = extractInnbyggerident(fellesformat)

        val dialogmelding = dialogmeldingXml.toDialogmelding(
            dialogmeldingId = "1",
            dialogmeldingType = dialogmeldingType,
            signaturDato = msgHead.msgInfo.genDate,
            navnHelsePersonellNavn = behandlerNavn
        )

        val pdfPayload = createPdfPayload(
            dialogmelding,
            validationResult, //kan være null, brukes kun hvis det er feil i validering
            innbyggerIdent!!,
            pasientNavn,
            legeIdent, //blir fnr ikke navn
            antallVedlegg
        )

         val httpClient = HttpClient(Apache, config)

        val pdfgenClient = PdfgenClient(
            url = "http://localhost:8080/api/v1/genpdf/padm2/padm2",
            httpClient = httpClient,
        )
        runBlocking {
            val pdf = pdfgenClient.createPdf(pdfPayload)
            val path = Paths.get("").toAbsolutePath().toString()
            File("joarkpdf/633889387.pdf").writeBytes(pdf)

        }



    }
}
