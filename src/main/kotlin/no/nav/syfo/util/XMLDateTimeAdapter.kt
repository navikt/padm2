package no.nav.syfo.util

import com.migesok.jaxb.adapter.javatime.LocalDateTimeXmlAdapter
import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import java.text.ParsePosition
import java.time.*
import java.time.format.DateTimeFormatter
import javax.xml.bind.DatatypeConverter

class XMLDateTimeAdapter : LocalDateTimeXmlAdapter() {
    override fun unmarshal(stringValue: String?): LocalDateTime? = when (stringValue) {
        null -> null
        else -> {
            if (hasTimeZoneInformation(stringValue))
                ZonedDateTime.parse(stringValue).withZoneSameInstant(ZoneId.of("Europe/Oslo")).toLocalDateTime()
            else LocalDateTime.parse(stringValue)
        }
    }
}

fun hasTimeZoneInformation(text: CharSequence): Boolean {
    val position = ParsePosition(0)
    val temporalAccessor = DateTimeFormatter.ISO_ZONED_DATE_TIME.parseUnresolved(text, position)
    return temporalAccessor != null && position.errorIndex < 0 && position.index >= text.length
}

class XMLDateAdapter : LocalDateXmlAdapter() {
    override fun unmarshal(stringValue: String?): LocalDate? = when (stringValue) {
        null -> null
        else -> DatatypeConverter.parseDate(stringValue).toInstant().atZone(ZoneOffset.MAX).toLocalDate()
    }
}
