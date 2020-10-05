package no.nav.syfo.client

import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.arenadialognotat.ArenaDialogNotat
import no.nav.helse.arenadialognotat.DokumentInfoType
import no.nav.helse.arenadialognotat.EiaDokumentInfoType
import no.nav.helse.arenadialognotat.LegeType
import no.nav.helse.arenadialognotat.NavnType
import no.nav.helse.arenadialognotat.PasientDataType
import no.nav.helse.arenadialognotat.PersonType
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.log
import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.arenaDialogNotatMarshaller
import no.nav.syfo.util.extractDialogmelding
import no.nav.syfo.util.toString

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
            avsender = EiaDokumentInfoType.Avsender().apply {
                lege = LegeType().apply {
                    legeFnr = legefnr
                    tssId = tssid?.toBigInteger() ?: "0".toBigInteger()
                    legeNavn = NavnType().apply {
                        fornavn = hcp.givenName
                        mellomnavn = hcp?.middleName ?: ""
                        etternavn = hcp?.familyName
                    }
                }
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
        svarReferanse = dialogmeldingXml.notat?.firstOrNull()?.dokIdNotat ?: ""
        notatDato = msgHead.msgInfo.genDate
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
