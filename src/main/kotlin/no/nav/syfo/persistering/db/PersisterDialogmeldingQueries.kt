package no.nav.syfo.persistering.db

import no.nav.syfo.db.*
import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.db.domain.DialogmeldingTidspunkt
import no.nav.syfo.util.objectMapper
import org.postgresql.util.PGobject
import java.sql.*
import java.time.LocalDateTime

fun DatabaseInterface.lagreMottattDialogmelding(
    receivedDialogmelding: ReceivedDialogmelding,
    sha256String: String,
) {
    connection.use { connection ->
        connection.opprettDialogmeldingOpplysninger(receivedDialogmelding)
        connection.opprettDialogmeldingDokument(receivedDialogmelding.dialogmelding, sha256String)
        connection.commit()
    }
}

fun DatabaseInterface.lagreMottattDialogmeldingValidering(
    receivedDialogmelding: ReceivedDialogmelding,
    validationResult: ValidationResult,
) {
    connection.use { connection ->
        connection.opprettBehandlingsutfall(validationResult, receivedDialogmelding.dialogmelding.id)
        connection.commit()
    }
}

fun ResultSet.toDialogmeldingTidspunkt(): DialogmeldingTidspunkt =
    DialogmeldingTidspunkt(
        signaturDato = getString("signaturDato"),
        mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
    )

fun Connection.opprettDialogmeldingOpplysninger(receivedDialogmelding: ReceivedDialogmelding): String {
    val ids = this.prepareStatement(
        """
            INSERT INTO DIALOGMELDINGOPPLYSNINGER(
                id,
                pasient_fnr,
                lege_fnr,
                mottak_id,
                msg_id,
                legekontor_org_nr,
                legekontor_her_id,
                mottatt_tidspunkt,
                fellesformat,
                journalforing,
                dialogmelding_published,
                arena,
                apprec,
                is_sent_to_arena
                )
            VALUES  (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """
    ).use {
        it.setString(1, receivedDialogmelding.dialogmelding.id)
        it.setString(2, receivedDialogmelding.personNrPasient)
        it.setString(3, receivedDialogmelding.personNrLege)
        it.setString(4, receivedDialogmelding.navLogId)
        it.setString(5, receivedDialogmelding.msgId)
        it.setString(6, receivedDialogmelding.legekontorOrgNr)
        it.setString(7, receivedDialogmelding.legekontorHerId)
        it.setTimestamp(8, Timestamp.valueOf(receivedDialogmelding.mottattDato))
        it.setString(9, receivedDialogmelding.fellesformat)
        it.setNull(10, Types.TIMESTAMP)
        it.setNull(11, Types.TIMESTAMP)
        it.setNull(12, Types.TIMESTAMP)
        it.setNull(13, Types.TIMESTAMP)
        it.setBoolean(14, false)
        it.executeQuery().toList { getString("id") }
    }

    if (ids.size != 1) {
        throw SQLException("Creating DIALOGMELDINGOPPLYSNINGER failed, no rows affected.")
    }
    return ids.first()
}

private fun Connection.opprettDialogmeldingDokument(dialogmelding: Dialogmelding, sha256String: String) {
    this.prepareStatement(
        """
            INSERT INTO DIALOGMELDINGDOKUMENT(id, dialogmelding, sha_string) VALUES  (?, ?, ?)
            """
    ).use {
        it.setString(1, dialogmelding.id)
        it.setObject(2, dialogmelding.toPGObject())
        it.setString(3, sha256String)
        it.executeUpdate()
    }
}

fun Connection.opprettBehandlingsutfall(validationResult: ValidationResult, dialogmeldingid: String) {
    this.prepareStatement(
        """
                INSERT INTO BEHANDLINGSUTFALL(id, behandlingsutfall) VALUES (?, ?)
            """
    ).use {
        it.setString(1, dialogmeldingid)
        it.setObject(2, validationResult.toPGObject())
        it.executeUpdate()
    }
}

fun DatabaseInterface.erDialogmeldingOpplysningerLagret(dialogmeldingid: String) =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM DIALOGMELDINGOPPLYSNINGER
                WHERE id=?;
                """
        ).use {
            it.setString(1, dialogmeldingid)
            it.executeQuery().next()
        }
    }

fun DatabaseInterface.lagreJournalforing(dialogmeldingid: String, journalpostId: String) {
    connection.use { connection ->
        connection.prepareStatement(
            """
                UPDATE DIALOGMELDINGOPPLYSNINGER 
                SET JOURNALFORING=?, JOURNALPOSTID=?
                WHERE ID=?;
                """
        ).use {
            it.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()))
            it.setString(2, journalpostId)
            it.setString(3, dialogmeldingid)
            val updated = it.executeUpdate()
            if (updated != 1) {
                throw SQLException("Expected a single row to be updated, got update count $updated")
            }
        }
        connection.commit()
    }
}

fun DatabaseInterface.hentDialogmeldingOpplysningerJournalpostId(dialogmeldingid: String) =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT journalpostid
                FROM DIALOGMELDINGOPPLYSNINGER
                WHERE id=?;
                """
        ).use {
            it.setString(1, dialogmeldingid)
            val list = it.executeQuery().toList { getString("journalpostid") }
            list.firstOrNull()
        }
    }

private const val queryGetArena = """
    SELECT arena
    FROM DIALOGMELDINGOPPLYSNINGER
    WHERE id=?;
"""

fun DatabaseInterface.erDialogmeldingOpplysningerSendtArena(dialogmeldingid: String) =
    connection.use { connection ->
        connection.prepareStatement(queryGetArena).use {
            it.setString(1, dialogmeldingid)
            val list = it.executeQuery().toList { getTimestamp("arena") }
            list.isNotEmpty() && list.firstOrNull() != null
        }
    }

private const val queryUpdateArenaSendt = """
    UPDATE DIALOGMELDINGOPPLYSNINGER
    SET arena = ?, is_sent_to_arena = ?
    WHERE id = ?;
"""

fun DatabaseInterface.lagreSendtArena(dialogmeldingid: String, isSent: Boolean) {
    connection.use { connection ->
        connection.lagreSendtArena(
            dialogmeldingId = dialogmeldingid,
            isSent = isSent,
            commit = true,
        )
    }
}

fun Connection.lagreSendtArena(dialogmeldingId: String, isSent: Boolean, commit: Boolean = false) {
    prepareStatement(queryUpdateArenaSendt).use {
        it.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()))
        it.setBoolean(2, isSent)
        it.setString(3, dialogmeldingId)
        val updated = it.executeUpdate()
        if (updated != 1) {
            throw SQLException("Expected a single row to be updated, got update count $updated")
        }
    }
    if (commit) {
        commit()
    }
}

fun DatabaseInterface.erDialogmeldingOpplysningerSendtKafka(dialogmeldingid: String) =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT dialogmelding_published
                FROM DIALOGMELDINGOPPLYSNINGER
                WHERE id=?;
                """
        ).use {
            it.setString(1, dialogmeldingid)
            val list = it.executeQuery().toList { getTimestamp("dialogmelding_published") }
            list.isNotEmpty() && list.firstOrNull() != null
        }
    }

fun DatabaseInterface.lagreSendtKafka(dialogmeldingid: String) {
    connection.use { connection ->
        connection.prepareStatement(
            """
                UPDATE DIALOGMELDINGOPPLYSNINGER 
                SET dialogmelding_published=?
                WHERE ID=?;
                """
        ).use {
            it.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()))
            it.setString(2, dialogmeldingid)
            val updated = it.executeUpdate()
            if (updated != 1) {
                throw SQLException("Expected a single row to be updated, got update count $updated")
            }
        }
        connection.commit()
    }
}

fun DatabaseInterface.erDialogmeldingOpplysningerSendtApprec(dialogmeldingid: String) =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT apprec
                FROM DIALOGMELDINGOPPLYSNINGER
                WHERE id=?;
                """
        ).use {
            it.setString(1, dialogmeldingid)
            val list = it.executeQuery().toList { getTimestamp("apprec") }
            list.isNotEmpty() && list.firstOrNull() != null
        }
    }

fun DatabaseInterface.lagreSendtApprec(dialogmeldingid: String) {
    connection.use { connection ->
        connection.prepareStatement(
            """
                UPDATE DIALOGMELDINGOPPLYSNINGER 
                SET apprec=?
                WHERE ID=?;
                """
        ).use {
            it.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()))
            it.setString(2, dialogmeldingid)
            val updated = it.executeUpdate()
            if (updated != 1) {
                throw SQLException("Expected a single row to be updated, got update count $updated")
            }
        }
        connection.commit()
    }
}

fun DatabaseInterface.hentIkkeFullforteDialogmeldinger() =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT id, fellesformat, mottatt_tidspunkt
                FROM dialogmeldingopplysninger
                WHERE apprec IS NULL AND mottatt_tidspunkt < (NOW() - INTERVAL '10 minutes')
                ORDER BY mottatt_tidspunkt ASC
                """
        ).use {
            it.executeQuery().toList {
                Triple(
                    first = getString("id"),
                    second = getString("fellesformat"),
                    third = getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
                )
            }
        }
    }

fun DatabaseInterface.hentFellesformat(
    msgId: String,
): String? =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT fellesformat
                FROM dialogmeldingopplysninger
                WHERE msg_id = ?
                ORDER BY mottatt_tidspunkt DESC
                """
        ).use {
            it.setString(1, msgId)
            it.executeQuery().toList {
                getString("fellesformat")
            }.firstOrNull()
        }
    }

fun DatabaseInterface.hentMottattTidspunkt(shaString: String) =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT dok.dialogmelding -> 'signaturDato' AS signaturDato, o.mottatt_tidspunkt
                FROM dialogmeldingdokument dok
                INNER JOIN dialogmeldingopplysninger o ON (o.id = dok.id)
                INNER JOIN BEHANDLINGSUTFALL utfall ON (dok.id = utfall.id)
                WHERE dok.sha_string=? AND utfall.behandlingsutfall ->> 'status' = 'OK'
                ORDER BY o.mottatt_tidspunkt ASC
                """
        ).use {
            it.setString(1, shaString)
            it.executeQuery().toList { toDialogmeldingTidspunkt() }.first()
        }
    }

fun DatabaseInterface.hasSavedDialogmeldingDokument(dialogmeldingId: String, shaString: String): Boolean =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT dok.*
                FROM DIALOGMELDINGDOKUMENT dok INNER JOIN BEHANDLINGSUTFALL utfall ON (dok.id = utfall.id)
                WHERE dok.id != ? AND dok.sha_string=? AND utfall.behandlingsutfall ->> 'status' = 'OK';
                """
        ).use {
            it.setString(1, dialogmeldingId)
            it.setString(2, shaString)
            it.executeQuery().next()
        }
    }

fun DatabaseInterface.getUnpublishedArenaMeldinger(): List<Pair<String, String>> =
    connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT id, fellesformat
            FROM dialogmeldingopplysninger d
            WHERE arena IS NULL
                AND apprec IS NOT NULL
                AND apprec < (NOW() - INTERVAL '10 minutes')
                AND dialogmelding_published IS NOT NULL
                AND EXISTS (
                    SELECT 1
                    FROM behandlingsutfall b
                    WHERE b.id = d.id AND behandlingsutfall ->> 'status' = 'OK'
                );
            """
        ).use {
            it.executeQuery().toList {
                Pair(
                    first = getString("id"),
                    second = getString("fellesformat")
                )
            }
        }
    }

fun Dialogmelding.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}

fun ValidationResult.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}
