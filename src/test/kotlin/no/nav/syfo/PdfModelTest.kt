package no.nav.syfo

import no.nav.syfo.util.objectMapper
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDateTime
import java.util.UUID
import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.model.Foresporsel
import no.nav.syfo.model.ForesporselFraSaksbehandlerForesporselSvar
import no.nav.syfo.model.Helsepersonell
import no.nav.syfo.model.HenvendelseFraLegeHenvendelse
import no.nav.syfo.model.InnkallingMoterespons
import no.nav.syfo.model.PdfModel
import no.nav.syfo.model.Person
import no.nav.syfo.model.RolleNotat
import no.nav.syfo.model.RollerRelatertNotat
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.TemaKode
import no.nav.syfo.model.TypeForesp
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.sanitizeForPdfGen
import org.junit.Test

internal class PdfModelTest {

    @Test
    internal fun `Creates a static pdfpayload`() {

        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"

        val dialogmelding = Dialogmelding(
            id = dialogmeldingId,
            henvendelseFraLegeHenvendelse = HenvendelseFraLegeHenvendelse(
                temaKode = TemaKode(
                    "2.16.578.1.12.4.1.1.8128",
                    "Henvendelse om pasient som ikke er sykmeldt",
                    "2",
                    "3",
                    "32",
                    "Henvendelse fra lege"

                ),
                tekstNotatInnhold = "Jeg er klar 12.30. Mvh, Lege legsesn",
                dokIdNotat = "",
                foresporsel = Foresporsel(
                    typeForesp = TypeForesp(
                        "Forespørsel om pasient",
                        "2.16.578.1.12.4.1.1.8129",
                        "1"
                    ),
                    sporsmal = "Ønsker info om pasient",
                    dokIdForesp = "OD1812186729156",
                    rollerRelatertNotat = RollerRelatertNotat(
                        rolleNotat = RolleNotat(
                            "2.16.578.1.12.4.1.1.9057", "1"
                        ),
                        person = Person(
                            "jon",
                            "Person"
                        ),
                        helsepersonell = Helsepersonell(
                            "helse",
                            "Person"
                        )
                    )
                ),
                rollerRelatertNotat = null
            ),
            innkallingMoterespons = InnkallingMoterespons(
                temaKode = TemaKode(
                    "2.16.578.1.12.4.1.1.8126",
                    "Ja, jeg kommer",
                    "1",
                    "1",
                    "11",
                    "Svar innkalling dialogmøte:Ja, jeg kommer"
                ),
                tekstNotatInnhold = "Hei,Det gjelder pas. Sender som vedlegg epikrisen",
                dokIdNotat = "A1578B81-0042-453B-8527-6CF182BDA6C7",
                foresporsel = Foresporsel(
                    typeForesp = TypeForesp(
                        "Forespørsel om pasient",
                        "2.16.578.1.12.4.1.1.8129",
                        "1"
                    ),
                    sporsmal = "Ønsker info om pasient",
                    dokIdForesp = "OD1812186729156",
                    rollerRelatertNotat = RollerRelatertNotat(
                        rolleNotat = RolleNotat(
                            "2.16.578.1.12.4.1.1.9057", "1"
                        ),
                        person = Person(
                            "jon",
                            "Person"
                        ),
                        helsepersonell = null
                    )
                )
            ),
            foresporselFraSaksbehandlerForesporselSvar = ForesporselFraSaksbehandlerForesporselSvar(
                temaKode = TemaKode(
                    "2.16.578.1.12.4.1.1.8129",
                    "Forespørsel om pasient",
                    "1",
                    "",
                    "",
                    ""
                ),
                tekstNotatInnhold = "Pasieten har masse info her",
                dokIdNotat = "OD1812186729156",
                datoNotat = LocalDateTime.now()
            ),
            navnHelsepersonell = navnHelsePersonellNavn,
            signaturDato = signaturDato
        )
        val pdfPayload = PdfModel(
            dialogmelding = dialogmelding,
            validationResult = ValidationResult(
                status = Status.INVALID,
                ruleHits = listOf(
                    RuleInfo(
                        ruleName = "PASIENT_YNGRE_ENN_13",
                        messageForUser = "Pasienten er under 13 år. Sykmelding kan ikke benyttes.",
                        messageForSender = "Pasienten er under 13 år. Sykmelding kan ikke benyttes.",
                        ruleStatus = Status.INVALID
                    ),
                    RuleInfo(
                        ruleName = "PASIENT_ELDRE_ENN_70",
                        messageForUser = "Sykmelding kan ikke benyttes etter at du har fylt 70 år",
                        messageForSender = "Pasienten er over 70 år. Sykmelding kan ikke benyttes.",
                        ruleStatus = Status.INVALID
                    )
                )
            ),
            pasientFnr = "3214141414",
            pasientNavn = "Koronasen, Kovid Nitten",
            navnSignerendeLege = "Legesen, Leg E.",
            antallVedlegg = 1,
        )
        println(objectMapper.writeValueAsString(pdfPayload))
        objectMapper.writeValueAsString(pdfPayload) shouldBeEqualTo objectMapper.writeValueAsString(pdfPayload.sanitizeForPdfGen())
    }
}
