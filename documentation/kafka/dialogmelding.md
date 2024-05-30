## Kafka Topic

### Beskrivelse
Topic inneholder innkomne dialogmeldinger fra fastleger og andre behandlere. Meldingene kommer fra ulike ElektroniskPasientJournal-systemer (EPJ-systemer) bruk på legekontor rundt omkring i landet, sendt via Norsk Helsenett, innom emottak og til slutt inn hos oss.

### Topic navn
teamsykefravr.dialogmelding

### Properties

Beskrivelse av feltene i en record ([DialogmeldingForKafka](../../src/main/kotlin/no/nav/syfo/kafka/DialogmeldingForKafka.kt)).
Feltet `dialogmelding` kan finnes her ([Dialogmelding](../../src/main/kotlin/no/nav/syfo/model/Dialogmelding.kt))

* `msgId: String` Id for meldingen, som vi får via en XML fra emottak
* `msgType: String` Type melding, som vi får via en XML fra emottak
* `navLogId: String` En ´ediLoggId´ som emottak legger på meldingen fra Norsk Helsenett.
* `mottattTidspunkt: LocalDateTime` Tidspunkt for når meldingen ble mottatt hos emottak
* `conversationRef: String or Null` Referanse til en samtale, dersom det har blitt svart på en melding sendt ut av oss.
* `parentRef: String or Null` Referanse til en melding, dersom det er et svar på en tidligere melding.
* `personIdentPasient: String` Personidenten til den sykmeldte/pasienten (fnr/dnr)
* `pasientAktoerId: String or Null // deprecated` Brukes ikke lenger
* `personIdentBehandler: String` Personidenten til behandleren som har sendt meldingen (fnr/dnr)
* `behandlerAktoerId: String or Null // deprecated` Brukes ikke lenger
* `legekontorOrgNr: String or Null` Organisasjonsnummeret til legekontoret der meldingen er sendt fra
* `legekontorHerId: String or Null` Helsenett-identifikator (HER-id) for legekontoret der meldingen er sendt fra
* `legekontorReshId: String or Null // deprecated` Brukes ikke lenger
* `legekontorOrgName: String` Navn på legekontoret der meldingen er sendt fra
* `legehpr: String or Null` Helsepersonellregister-nummeret (HPR-nr) til behandleren som har sendt meldingen. Denne er unik for en behandler
* `dialogmelding: Dialogmelding` Se [Dialogmelding](../../src/main/kotlin/no/nav/syfo/model/Dialogmelding.kt)
* `antallVedlegg: Int` Antall vedlegg som følger med meldingen
* `journalpostId: String` Journalpost-id til journalposten lagret i Joark
* `fellesformatXML: String` XML-en som ble sendt til oss fra avsender. Inneholder ikke eventuelle vedlegg