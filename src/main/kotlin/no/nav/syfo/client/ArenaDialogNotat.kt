package no.nav.syfo.client

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
import no.nav.syfo.application.mq.MQSenderInterface
import no.nav.syfo.logger
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.arenaDialogNotatMarshaller
import no.nav.syfo.util.extractBehandler
import no.nav.syfo.util.extractDialogmelding
import no.nav.syfo.util.toString

const val MAKS_TEKSTLENGDE = 2000

fun createArenaDialogNotat(
    fellesformat: XMLEIFellesformat,
    tssid: String?,
    legefnr: String,
    innbyggerident: String,
    msgHead: XMLMsgHead,
    receiverBlock: XMLMottakenhetBlokk,
    dialogmelding: Dialogmelding
): ArenaDialogNotat =
    ArenaDialogNotat().apply {
        val dialogmeldingXml = extractDialogmelding(fellesformat)
        val behandler = extractBehandler(fellesformat)
        eiaDokumentInfo = EiaDokumentInfoType().apply {
            dokumentInfo = DokumentInfoType().apply {
                dokumentType = "DM"
                dokumentTypeVersjon = "1.0"
                dokumentNavn = msgHead.msgInfo.type.dn
                dokumentreferanse = msgHead.msgInfo.msgId
                ediLoggId = receiverBlock.ediLoggId
                dokumentDato = msgHead.msgInfo.genDate
            }
            avsender = createAvsender(legefnr, tssid, behandler)
            avsenderSystem = EiaDokumentInfoType.AvsenderSystem().apply {
                systemNavn = "SKJEMA-MOTTAK"
                systemVersjon = "1.0.0"
            }
        }
        pasientData = PasientDataType().apply {
            person = PersonType().apply {
                personFnr = innbyggerident
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

fun createAvsender(
    legefnr: String,
    tssid: String?,
    behandler: Behandler?
): EiaDokumentInfoType.Avsender {
    val validatedTssID = if (tssid.isNullOrBlank()) "0" else tssid
    return EiaDokumentInfoType.Avsender().apply {
        lege = LegeType().apply {
            legeFnr = legefnr
            tssId = validatedTssID.toBigInteger()
            legeNavn = NavnType().apply {
                fornavn = behandler?.fornavn ?: ""
                mellomnavn = behandler?.mellomnavn ?: ""
                etternavn = behandler?.etternavn ?: ""
            }
        }
    }
}

fun findArenaNotatKategori(dialogmelding: Dialogmelding): String {
    return when {
        dialogmelding.foresporselFraSaksbehandlerForesporselSvar != null -> {
            dialogmelding.foresporselFraSaksbehandlerForesporselSvar!!.temaKode.arenaNotatKategori
        }
        dialogmelding.henvendelseFraLegeHenvendelse != null -> {
            dialogmelding.henvendelseFraLegeHenvendelse!!.temaKode.arenaNotatKategori
        }
        dialogmelding.innkallingMoterespons != null -> {
            dialogmelding.innkallingMoterespons!!.temaKode.arenaNotatKategori
        }
        else -> throw RuntimeException("Ugyldig dialogmeldingtype")
    }
}

fun findArenaNotatKode(dialogmelding: Dialogmelding): String {
    return when {
        dialogmelding.foresporselFraSaksbehandlerForesporselSvar != null -> {
            dialogmelding.foresporselFraSaksbehandlerForesporselSvar!!.temaKode.arenaNotatKode
        }
        dialogmelding.henvendelseFraLegeHenvendelse != null -> {
            dialogmelding.henvendelseFraLegeHenvendelse!!.temaKode.arenaNotatKode
        }
        dialogmelding.innkallingMoterespons != null -> {
            dialogmelding.innkallingMoterespons!!.temaKode.arenaNotatKode
        }
        else -> throw RuntimeException("Ugyldig dialogmeldingtype")
    }
}

fun findArenaNotatTittel(dialogmelding: Dialogmelding): String {
    return when {
        dialogmelding.foresporselFraSaksbehandlerForesporselSvar != null -> {
            dialogmelding.foresporselFraSaksbehandlerForesporselSvar!!.temaKode.arenaNotatTittel
        }
        dialogmelding.henvendelseFraLegeHenvendelse != null -> {
            dialogmelding.henvendelseFraLegeHenvendelse!!.temaKode.arenaNotatTittel
        }
        dialogmelding.innkallingMoterespons != null -> {
            dialogmelding.innkallingMoterespons!!.temaKode.arenaNotatTittel
        }
        else -> throw RuntimeException("Ugyldig dialogmeldingtype")
    }
}

fun sendArenaDialogNotat(
    mqSender: MQSenderInterface,
    arenaDialogNotat: ArenaDialogNotat,
    loggingMeta: LoggingMeta
) {
    mqSender.sendArena(
        payload = arenaDialogNotatMarshaller.toString(arenaDialogNotat)
    )
    logger.info("Message is sent to arena {}", fields(loggingMeta))
}
