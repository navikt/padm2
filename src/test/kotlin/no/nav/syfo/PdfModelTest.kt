package no.nav.syfo

import java.time.LocalDateTime
import java.util.UUID
import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.model.DialogmeldingForesporselOmPasient
import no.nav.syfo.model.DialogmeldingHenvendelseFraLege
import no.nav.syfo.model.DialogmeldingInnkallingDialogmote
import no.nav.syfo.model.Foresporsel
import no.nav.syfo.model.PdfModel
import no.nav.syfo.model.Person
import no.nav.syfo.model.RolleNotat
import no.nav.syfo.model.RollerRelatertNotat
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.TemaKode
import no.nav.syfo.model.TypeForesp
import no.nav.syfo.model.ValidationResult
import org.junit.Test

internal class PdfModelTest {

    @Test
    internal fun `Creates a static pdfpayload`() {

        val dialogmeldingId = UUID.randomUUID().toString()
        val signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        val navnHelsePersonellNavn = "Per Hansen"

        val dialogmelding = Dialogmelding(
            id = dialogmeldingId,
            dialogmeldingHenvendelseFraLege = DialogmeldingHenvendelseFraLege(
                teamakode = TemaKode(
                    "Henvendelse om sykefraværsoppfølging",
                    "Benyttes når henvendelsen gjelder en pasient som er sykmeldt, evt henvendelser " +
                            "knyttet til et tidligere sykefraværstilfelle.",
                    "2.16.578.1.12.4.1.1.8128",
                    "1"
                ),
                tekstNotatInnhold = "Jeg er klar 12.30. Mvh, Lege legsesn"
            ),
            dialogmeldingInnkallingDialogmote = DialogmeldingInnkallingDialogmote(
                teamakode = TemaKode(
                    "Henvendelse om sykefraværsoppfølging", null, "2.16.578.1.12.4.1.1.8128", "1"
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
                        )
                    )
                )
            ),
            dialogmeldingForesporselOmPasier = DialogmeldingForesporselOmPasient(
                teamakode = TemaKode("Svar på forespørsel", "Ingen", "2.16.578.1.12.4.1.1.9069", "5"),
                tekstNotatInnhold = "Pasieten har masse info her",
                dokIdNotat = "OD1812186729156",
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
                        )
                    )
                )
            ),
            navnHelsepersonell = navnHelsePersonellNavn,
            signaturDato = signaturDato
        )
        val pdfPayload = PdfModel(
            dialogmelding = dialogmelding,
            validationResult = ValidationResult(
                status = Status.INVALID, ruleHits = listOf(
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
            pasientFnr = "3214141414"
        )
        println(objectMapper.writeValueAsString(pdfPayload))
    }
}
