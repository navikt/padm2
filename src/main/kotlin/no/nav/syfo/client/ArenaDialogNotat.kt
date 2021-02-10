package no.nav.syfo.client

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.arenadialognotat.*
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLHealthcareProfessional
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.log
import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.arenaDialogNotatMarshaller
import no.nav.syfo.util.extractDialogmelding
import no.nav.syfo.util.toString
import javax.jms.MessageProducer
import javax.jms.Session

const val MAKS_TEKSTLENGDE = 2000

fun createArenaDialogNotat(
    fellesformat: XMLEIFellesformat,
    tssid: String?,
    legefnr: String,
    pasientFnr: String,
    msgHead: XMLMsgHead,
    receiverBlock: XMLMottakenhetBlokk,
    dialogmelding: Dialogmelding
):
        ArenaDialogNotat =
    ArenaDialogNotat().apply {
        val org = msgHead.msgInfo.sender.organisation
        val hcp = org.healthcareProfessional
        val dialogmeldingXml = extractDialogmelding(fellesformat)
        eiaDokumentInfo = EiaDokumentInfoType().apply {
            dokumentInfo = DokumentInfoType().apply {
                dokumentType = "DM"
                dokumentTypeVersjon = "1.0"
                dokumentNavn = msgHead.msgInfo.type.dn
                dokumentreferanse = msgHead.msgInfo.msgId
                ediLoggId = receiverBlock.ediLoggId
                dokumentDato = msgHead.msgInfo.genDate
            }
            avsender = createAvsenderLege(legefnr, tssid, hcp)
            avsenderSystem = EiaDokumentInfoType.AvsenderSystem().apply {
                systemNavn = "SKJEMA-MOTTAK"
                systemVersjon = "1.0.0"
            }
        }
        pasientData = PasientDataType().apply {
            person = PersonType().apply {
                personFnr = pasientFnr
                personNavn = NavnType().apply {
                    fornavn = msgHead.msgInfo.patient.givenName
                    mellomnavn = msgHead.msgInfo.patient?.middleName ?: ""
                    etternavn = msgHead.msgInfo.patient.familyName
                }
            }
        }
        notatKategori = findArenaNotatKategori(dialogmelding)
        notatKode = findArenaNotatKode(dialogmelding)
        notatTittel = findArenaNotatTittel(dialogmelding)
        notatTekst = dialogmeldingXml.notat?.firstOrNull()?.tekstNotatInnhold.toString()

        if (notatTekst.length > MAKS_TEKSTLENGDE) {
            val kuttetTekst = " [Ufullstendig tekst, les mer i Gosys]"
            notatTekst = "${notatTekst.substring(0, MAKS_TEKSTLENGDE - kuttetTekst.length)}$kuttetTekst"
        }

        svarReferanse = dialogmeldingXml.notat?.firstOrNull()?.dokIdNotat ?: ""
        notatDato = msgHead.msgInfo.genDate
    }

fun createAvsenderLege(
    legefnr: String,
    tssid: String?,
    healthCareProfessional: XMLHealthcareProfessional
): EiaDokumentInfoType.Avsender {
    val validatedTssID = if (tssid.isNullOrBlank()) "0" else tssid
    return EiaDokumentInfoType.Avsender().apply {
        lege = LegeType().apply {
            legeFnr = legefnr
            tssId = validatedTssID.toBigInteger()
            legeNavn = NavnType().apply {
                fornavn = healthCareProfessional.givenName
                mellomnavn = healthCareProfessional?.middleName ?: ""
                etternavn = healthCareProfessional?.familyName
            }
        }
    }
}

fun findArenaNotatKategori(dialogmelding: Dialogmelding): String {
    when {
        dialogmelding.foresporselFraSaksbehandlerForesporselSvar != null -> {
            return dialogmelding.foresporselFraSaksbehandlerForesporselSvar!!.teamakode.arenaNotatKategori
        }
        dialogmelding.henvendelseFraLegeHenvendelse != null -> {
            return dialogmelding.henvendelseFraLegeHenvendelse!!.teamakode.arenaNotatKategori
        }
        dialogmelding.innkallingMoterespons != null -> {
            return dialogmelding.innkallingMoterespons!!.teamakode.arenaNotatKategori
        }
        else -> throw RuntimeException("Ugyldig dialogmeldingtype")
    }
}

fun findArenaNotatKode(dialogmelding: Dialogmelding): String {
    when {
        dialogmelding.foresporselFraSaksbehandlerForesporselSvar != null -> {
            return dialogmelding.foresporselFraSaksbehandlerForesporselSvar!!.teamakode.arenaNotatKode
        }
        dialogmelding.henvendelseFraLegeHenvendelse != null -> {
            return dialogmelding.henvendelseFraLegeHenvendelse!!.teamakode.arenaNotatKode
        }
        dialogmelding.innkallingMoterespons != null -> {
            return dialogmelding.innkallingMoterespons!!.teamakode.arenaNotatKode
        }
        else -> throw RuntimeException("Ugyldig dialogmeldingtype")
    }
}

fun findArenaNotatTittel(dialogmelding: Dialogmelding): String {
    when {
        dialogmelding.foresporselFraSaksbehandlerForesporselSvar != null -> {
            return dialogmelding.foresporselFraSaksbehandlerForesporselSvar!!.teamakode.arenaNotatTittel
        }
        dialogmelding.henvendelseFraLegeHenvendelse != null -> {
            return dialogmelding.henvendelseFraLegeHenvendelse!!.teamakode.arenaNotatTittel
        }
        dialogmelding.innkallingMoterespons != null -> {
            return dialogmelding.innkallingMoterespons!!.teamakode.arenaNotatTittel
        }
        else -> throw RuntimeException("Ugyldig dialogmeldingtype")
    }
}

fun sendArenaDialogNotat(
    producer: MessageProducer,
    session: Session,
    arenaDialogNotat: ArenaDialogNotat,
    loggingMeta: LoggingMeta
) = producer.send(session.createTextMessage().apply {
    text = arenaDialogNotatMarshaller.toString(arenaDialogNotat)
    log.info("Message is sendt to arena {}", fields(loggingMeta))
})
