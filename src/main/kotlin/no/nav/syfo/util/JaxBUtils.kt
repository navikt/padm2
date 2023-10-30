package no.nav.syfo.util

import com.migesok.jaxb.adapter.javatime.LocalDateTimeXmlAdapter
import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller
import no.nav.helse.apprecV1.XMLAppRec
import no.nav.helse.arenadialognotat.ArenaDialogNotat
import no.nav.helse.base64container.Base64Container
import no.nav.helse.dialogmelding.XMLDialogmelding
import no.nav.helse.eiFellesformat2.XMLEIFellesformat
import no.nav.helse.eiFellesformat2.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLSender
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.Source
import javax.xml.transform.sax.SAXSource

val fellesformatJaxBContext: JAXBContext = JAXBContext.newInstance(
    XMLEIFellesformat::class.java,
    XMLMsgHead::class.java,
    XMLMottakenhetBlokk::class.java,
    XMLDialogmelding::class.java,
    Base64Container::class.java,
    XMLAppRec::class.java,
)

fun getSenderMarshaller(): Marshaller = JAXBContext.newInstance(XMLSender::class.java).createMarshaller()
    .apply { setProperty(Marshaller.JAXB_ENCODING, "ISO-8859-1") }

val apprecJaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java, XMLAppRec::class.java)
fun getApprecMarshaller(): Marshaller = apprecJaxBContext.createMarshaller()

val arenaDialogNotatJaxBContext: JAXBContext = JAXBContext.newInstance(ArenaDialogNotat::class.java)
fun getArenaDialogNotatMarshaller(): Marshaller = arenaDialogNotatJaxBContext.createMarshaller()

fun Marshaller.toString(input: Any): String = StringWriter().use {
    marshal(input, it)
    it.toString()
}

fun safeUnmarshal(inputMessageText: String): XMLEIFellesformat {
    // Disable XXE
    val spf: SAXParserFactory = SAXParserFactory.newInstance()
    spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    spf.isNamespaceAware = true

    val xmlSource: Source =
        SAXSource(
            spf.newSAXParser().xmlReader,
            InputSource(StringReader(inputMessageText)),
        )
    return getFellesformatUnmarshaller().unmarshal(xmlSource) as XMLEIFellesformat
}

// Unmarshaller is not thread safe - do not "optimize"
private fun getFellesformatUnmarshaller(): Unmarshaller = fellesformatJaxBContext.createUnmarshaller().apply {
    setAdapter(LocalDateTimeXmlAdapter::class.java, XMLDateTimeAdapter())
    setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
}
