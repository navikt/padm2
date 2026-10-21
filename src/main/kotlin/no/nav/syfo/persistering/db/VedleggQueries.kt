package no.nav.syfo.persistering.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.Vedlegg
import java.sql.Connection

private const val queryLagreVedlegg = """
    INSERT INTO DIALOGMELDINGVEDLEGG (dialogmelding_id, vedlegg_nr, mime_type, beskrivelse, innhold)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT (dialogmelding_id, vedlegg_nr) DO NOTHING
"""

fun DatabaseInterface.lagreVedlegg(dialogmeldingId: String, vedleggNr: Int, vedlegg: Vedlegg) {
    connection.use { connection ->
        connection.lagreVedlegg(dialogmeldingId, vedleggNr, vedlegg)
        connection.commit()
    }
}

fun Connection.lagreVedlegg(dialogmeldingId: String, vedleggNr: Int, vedlegg: Vedlegg) {
    prepareStatement(queryLagreVedlegg).use {
        it.setString(1, dialogmeldingId)
        it.setInt(2, vedleggNr)
        it.setString(3, vedlegg.mimeType)
        it.setString(4, vedlegg.beskrivelse)
        it.setBytes(5, vedlegg.contentBase64)
        it.executeUpdate()
    }
}

private const val queryHentVedlegg = """
    SELECT mime_type, beskrivelse, innhold
    FROM DIALOGMELDINGVEDLEGG
    WHERE dialogmelding_id = ?
    ORDER BY vedlegg_nr
"""

fun DatabaseInterface.hentVedlegg(dialogmeldingId: String): List<Vedlegg> =
    connection.use { connection ->
        connection.prepareStatement(queryHentVedlegg).use {
            it.setString(1, dialogmeldingId)
            it.executeQuery().toList {
                Vedlegg(
                    mimeType = getString("mime_type"),
                    beskrivelse = getString("beskrivelse") ?: "",
                    contentBase64 = getBytes("innhold"),
                )
            }
        }
    }
