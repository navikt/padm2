package no.nav.syfo.util

import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller
import no.nav.helse.apprecV1.XMLAppRec
import no.nav.helse.base64container.Base64Container
import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLSender

val fellesformatJaxBContext: JAXBContext = JAXBContext.newInstance(
    XMLEIFellesformat::class.java, XMLMsgHead::class.java,
    XMLMottakenhetBlokk::class.java, XMLDialogmelding::class.java, Base64Container::class.java, XMLAppRec::class.java)
val fellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller()
val fellesformatMarshaller: Marshaller = fellesformatJaxBContext.createMarshaller()

val dialogmeldingJaxBContext: JAXBContext = JAXBContext.newInstance(XMLDialogmelding::class.java)
val dialogmeldingMarshaller: Marshaller = dialogmeldingJaxBContext.createMarshaller()

val senderMarshaller: Marshaller = JAXBContext.newInstance(XMLSender::class.java).createMarshaller()
    .apply { setProperty(Marshaller.JAXB_ENCODING, "ISO-8859-1") }

fun Marshaller.toString(input: Any): String = StringWriter().use {
    marshal(input, it)
    it.toString()
}
