package no.nav.syfo.persistering

import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.dropData
import no.nav.syfo.kafka.helsemelding.generateIncomingDialogMessageJson
import no.nav.syfo.kafka.helsemelding.receivedDialogmeldingFromHelsemelding
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.persistering.db.hentIkkeFullforteDialogmeldinger
import no.nav.syfo.persistering.db.opprettDialogmeldingOpplysninger
import no.nav.syfo.updateCreatedAt
import no.nav.syfo.util.getFileAsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

class LagretDialogmeldingTest {

    private val database = ExternalMockEnvironment.instance.database

    @BeforeEach
    fun beforeEach() {
        database.dropData()
    }

    private fun lagreOgHentIkkeFullfort(receivedDialogmelding: ReceivedDialogmelding): ReceivedDialogmelding {
        database.connection.use {
            it.opprettDialogmeldingOpplysninger(receivedDialogmelding)
            it.commit()
        }
        database.updateCreatedAt(
            dialogmeldingId = receivedDialogmelding.dialogmelding.id,
            timestamp = Timestamp.valueOf(LocalDateTime.now().minusHours(1)),
        )
        val (lagretDialogmelding, _) = database.hentIkkeFullforteDialogmeldinger().single()
        return lagretDialogmelding.toReceivedDialogmelding()
    }

    @Test
    fun `rebuilds dialogmelding from eMottak`() {
        val receivedDialogmelding = ReceivedDialogmelding.create(
            dialogmeldingId = UUID.randomUUID().toString(),
            fellesformat = getFileAsString("src/test/resources/dialogmelding_dialog_notat.xml"),
        )

        assertEquals(receivedDialogmelding, lagreOgHentIkkeFullfort(receivedDialogmelding))
        assertTrue(kafkaMeldingErSqlNull(receivedDialogmelding.dialogmelding.id))
    }

    private fun kafkaMeldingErSqlNull(dialogmeldingId: String): Boolean =
        database.connection.use { connection ->
            connection.prepareStatement(
                "SELECT kafka_melding IS NULL AS er_null FROM dialogmeldingopplysninger WHERE id = ?"
            ).use {
                it.setString(1, dialogmeldingId)
                it.executeQuery().run {
                    next()
                    getBoolean("er_null")
                }
            }
        }

    @Test
    fun `rebuilds dialogmelding from Helsemelding`() {
        val receivedDialogmelding = receivedDialogmeldingFromHelsemelding(
            dialogmeldingId = UUID.randomUUID().toString(),
            kafkaMelding = generateIncomingDialogMessageJson(),
        )

        val gjenoppbygd = lagreOgHentIkkeFullfort(receivedDialogmelding)

        // kafka_melding er JSONB, så Postgres normaliserer formateringen.
        assertEquals(receivedDialogmelding.copy(kafkaMelding = null), gjenoppbygd.copy(kafkaMelding = null))
    }
}
