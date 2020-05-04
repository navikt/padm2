package no.nav.syfo.apprec

import java.time.LocalDateTime
import no.nav.helse.apprecV1.XMLAdditionalId
import no.nav.helse.apprecV1.XMLAppRec
import no.nav.helse.apprecV1.XMLCS
import no.nav.helse.apprecV1.XMLCV as AppRecCV
import no.nav.helse.apprecV1.XMLHCP
import no.nav.helse.apprecV1.XMLHCPerson
import no.nav.helse.apprecV1.XMLInst
import no.nav.helse.apprecV1.XMLOriginalMsgId
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLCV as MsgHeadCV
import no.nav.helse.msgHead.XMLHealthcareProfessional
import no.nav.helse.msgHead.XMLIdent
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLOrganisation
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.util.get

fun createApprec(fellesformat: XMLEIFellesformat, apprecStatus: ApprecStatus): XMLEIFellesformat {
    val xmlMottakenhetBlokk = fellesformat.get<XMLMottakenhetBlokk>()
    val xmlMsgHead = fellesformat.get<XMLMsgHead>()
    val fellesformatApprec = XMLEIFellesformat().apply {
        any.add(XMLMottakenhetBlokk().apply {
            ediLoggId = xmlMottakenhetBlokk.ediLoggId
            ebRole = ApprecConstant.ebRoleSaksbehandler.string
            ebService = xmlMottakenhetBlokk.ebService
            ebAction = ApprecConstant.ebActionBekreftelse.string
        }
        )
        any.add(XMLAppRec().apply {
            msgType = XMLCS().apply {
                v = ApprecConstant.apprec.string
            }
            miGversion = ApprecConstant.apprecVersionV1_0.string
            genDate = LocalDateTime.now()
            id = xmlMottakenhetBlokk.ediLoggId

            sender = XMLAppRec.Sender().apply {
                hcp = xmlMsgHead.msgInfo.receiver.organisation.intoHCP()
            }

            receiver = XMLAppRec.Receiver().apply {
                hcp = xmlMsgHead.msgInfo.sender.organisation.intoHCP()
            }

            status = XMLCS().apply {
                v = apprecStatus.v
                dn = apprecStatus.dn
            }

            originalMsgId = XMLOriginalMsgId().apply {
                msgType = XMLCS().apply {
                    v = xmlMsgHead.msgInfo.type.v
                    dn = xmlMsgHead.msgInfo.type.dn
                }
                issueDate = xmlMsgHead.msgInfo.genDate
                id = xmlMsgHead.msgInfo.msgId
            }
        }
        )
    }

    return fellesformatApprec
}

fun XMLHealthcareProfessional.intoHCPerson(): XMLHCPerson = XMLHCPerson().apply {
    name = if (middleName == null) "$familyName $givenName" else "$familyName $givenName $middleName"
    id = ident.first().id
    typeId = ident.first().typeId.intoCS()
    additionalId += ident.drop(1)
}

fun XMLOrganisation.intoHCP(): XMLHCP = XMLHCP().apply {
    inst = ident.first().intoInst().apply {
        name = organisationName
        additionalId += ident.drop(1)

        if (healthcareProfessional != null) {
            hcPerson += healthcareProfessional.intoHCPerson()
        }
    }
}

fun XMLIdent.intoInst(): XMLInst {
    val ident = this
    return XMLInst().apply {
        id = ident.id
        typeId = ident.typeId.intoCS()
    }
}

fun MsgHeadCV.intoCS(): XMLCS {
    val msgHeadCV = this
    return XMLCS().apply {
        dn = msgHeadCV.dn
        v = msgHeadCV.v
    }
}

operator fun MutableList<XMLAdditionalId>.plusAssign(idents: Iterable<XMLIdent>) {
    this.addAll(idents.map { it.intoAdditionalId() })
}

fun XMLIdent.intoAdditionalId(): XMLAdditionalId {
    val ident = this
    return XMLAdditionalId().apply {
        id = ident.id
        type = XMLCS().apply {
            dn = ident.typeId.dn
            v = ident.typeId.v
        }
    }
}

fun RuleInfo.toApprecCV(): AppRecCV {
    val ruleInfo = this
    return createApprecError(ruleInfo.messageForSender)
}

fun createApprecError(textToTreater: String): AppRecCV = AppRecCV().apply {
    dn = textToTreater
    v = "2.16.578.1.12.4.1.1.8221"
    s = "X99"
}
