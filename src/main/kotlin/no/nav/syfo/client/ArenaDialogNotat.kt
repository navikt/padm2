package no.nav.syfo.client

import java.time.LocalDateTime
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
import no.nav.syfo.model.DialogmeldingKodeverk
import no.nav.syfo.objectMapper
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.arenaDialogNotatMarshaller
import no.nav.syfo.util.extractDialogmelding
import no.nav.syfo.util.toString

fun createArenaDialogNotat(
    fellesformat: XMLEIFellesformat,
    tssid: String?,
    legefnr: String,
    pasientFnr: String,
    signaturDato: LocalDateTime,
    msgHead: XMLMsgHead,
    receiverBlock: XMLMottakenhetBlokk
):
        ArenaDialogNotat =
    ArenaDialogNotat().apply {
        val org = msgHead.msgInfo.sender.organisation
        val hcp = org.healthcareProfessional
        val dialogmelding = extractDialogmelding(fellesformat)
        eiaDokumentInfo = EiaDokumentInfoType().apply {
            dokumentInfo = DokumentInfoType().apply {
                dokumentType = "DM"
                dokumentTypeVersjon = "1.0"
                dokumentNavn = msgHead.msgInfo.type.dn
                dokumentreferanse = msgHead.msgInfo.msgId
                ediLoggId = receiverBlock.ediLoggId
                dokumentDato = signaturDato
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
        notatKategori = DialogmeldingKodeverk.values().firstOrNull {
            it.dn == dialogmelding.notat?.firstOrNull()?.temaKodet?.dn
        }?.arenaNotatKategori ?: ""
        notatKode = DialogmeldingKodeverk.values().firstOrNull {
            it.dn == dialogmelding.notat?.firstOrNull()?.temaKodet?.dn
        }?.arenaNotatKode ?: ""
        notatTittel = dialogmelding.notat?.firstOrNull()?.temaKodet?.dn ?: ""
        notatTekst = dialogmelding.notat?.firstOrNull()?.tekstNotatInnhold.toString()
        svarReferanse = dialogmelding.notat?.firstOrNull()?.dokIdNotat ?: ""
        notatDato = signaturDato
    }

fun sendArenaDialogNotat(
    producer: MessageProducer,
    session: Session,
    arenaDialogNotat: ArenaDialogNotat,
    loggingMeta: LoggingMeta
) = producer.send(session.createTextMessage().apply {
    log.info("Logger arena objekt: ${objectMapper.writeValueAsString(arenaDialogNotat)} {}", fields(loggingMeta))
    text = arenaDialogNotatMarshaller.toString(arenaDialogNotat)
    log.info("Message is sendt to arena {}", fields(loggingMeta))
})
