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
                sent_to_arena
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
    SET arena = ?, sent_to_arena = ?
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

fun DatabaseInterface.getUnpublishedArenaMeldinger(): List<Triple<String, String, String>> =
    connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT id, fellesformat, msg_id
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
                Triple(
                    first = getString("id"),
                    second = getString("fellesformat"),
                    third = getString("msg_id"),
                )
            }
        }
    }

fun DatabaseInterface.getAdhocArenaMeldinger(): List<Triple<String, String, String>> =
    connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT id, fellesformat, msg_id
            FROM dialogmeldingopplysninger d
            WHERE !sent_to_arena
                AND msg_id in (
                '675f2f92-7186-4e4c-a07c-64f179eddb3e',
                '460f7781-2f5c-4b30-b29a-b0a800d44210',
                'ea0bd034-70d3-4316-8cd7-b0a800d2bee0',
                '39e8ba93-e203-4d79-ba40-fe996c87c4da',
                'c6143f04-90e5-4d46-97b5-b0a800cf0c36',
                'e725a744-6fbd-41af-aca5-f4b19fb8ae59',
                '9b30a673-ea91-4c08-af64-bfbee8ea2b02',
                'e5785832-6211-4b49-85bb-37bb0f4b7ceb',
                '4ec8112d-17ba-4c52-a908-7fed7420c069',
                '754196ec-b4c1-4888-8869-54da46bb1d07',
                'f2149eca-4c0f-4e25-85f9-ae96eef367e0',
                '7552c26c-338a-4ce7-9a6c-c57ef1c7d243',
                'a8bc0671-b28c-4071-999e-726ba5223800',
                'd3b089d3-7b40-4e89-b20f-206e84fe3c48',
                'ce3f60e9-e45e-4195-8400-939f28ede026',
                '1811b0e0-66d6-407b-a771-b0a800c73624',
                '7ab8390f-7db7-45fa-81a0-d8a3cacd3cff',
                '6c9ec3fc-f4c1-4d40-8efd-ce6ecee0e018',
                'c7f58d69-31cb-4346-a103-b0a800c55a1c',
                'A98C7A9E-A9E3-44E6-B1A9-4F4430A4345C',
                '87ddbbcb-7d39-487a-9c73-9f8959394735',
                '23729df7-d512-4659-8cee-b0a800bf2831',
                '54465d7d-e7fc-4ee6-9383-b0a800bd82fd',
                'e02daf92-b22d-4c3f-a5ed-b0a800c00f92',
                'af5ffe1b-745c-4050-adcc-b0a800bf931d',
                '5c982d18-e8f0-4732-be3a-fec32a3f071d',
                '4fb9b378-8830-449d-aaa1-d164b007c1e1',
                '4e39fa95-110c-47c3-bcd0-d533e94ad039',
                'fe678595-2582-4590-9611-db5b14e8ad4b',
                'bcd50867-a920-484a-919f-339d5552884c',
                '3846e2ec-9ee6-40e4-970c-7e1a8a05d4c9',
                '9102b5ac-8622-4760-b24f-137366b27bed',
                '58a9722c-aab8-4c45-aed0-3207c0573cd1',
                '7aea6070-42a4-48e6-94d0-05a71076c614',
                'efd544e1-701c-4399-b24a-d52cdccc209e',
                'ccae4212-dba0-4497-8c9d-cd59a2cb102b',
                '47beadbf-daa1-47d3-a838-22476073dfff',
                '87a8fb6a-0162-4c3a-acf5-7606d32d2372',
                '8f0f729c-caf3-479c-aa5d-19b285064ace',
                'b5a92a27-619a-4846-a36d-5a731d8a75f0',
                '98c978d0-f032-4541-895d-d38b18a913fa',
                'a6f33e15-0341-4062-b6c3-1924140bcf22',
                '953de1cd-10a2-492d-b3db-5665c0b89e4f',
                '28880969-076c-46b5-9142-70b1a4dee525',
                '4000b871-8c10-41b0-9ecc-87af2683bb80',
                '5e647934-c6d8-43ce-9dc6-303a62e8b04c',
                'f0a87c59-a0a9-49f0-8f14-4f1db10cf288',
                'C9D224DD-96ED-4CD0-BBBC-4CB49E3A44BE',
                '42334ee8-07f9-4254-b3d3-2334d732dacf',
                'e170bcf8-0025-4563-95dc-5c5960bc1024',
                '56501219-c140-4c4a-9390-c3fe920e28dd',
                '1282e669-4a85-45a0-80dd-e2b17a45c108',
                '532e7f5a-81ce-4c9c-9b24-5bd6d063895e',
                'c2cf702a-9911-45d6-9808-bff967b36d06',
                'cbdd2ffd-1a31-4a0d-9b17-2e67b41aa0d0',
                '217d4d32-6465-4126-abcd-1765e29a91ed',
                'bd14fffa-f124-4e13-ac55-9bb43b7ea017',
                '63011ee9-9757-473a-bea6-2b4a8a8f7a0f',
                'd9af568f-289d-4ddd-a46a-77b5cb43e8f4',
                '3c663bfb-ddca-49d4-8c94-2587be3bbe29',
                'f4c9e429-aaa1-459f-b5e4-8470241addf1',
                '536ea26b-0c72-432b-bb63-1c290c2ee9c3',
                'ff074dc5-3346-4844-a5f0-0f64d6f76c7f',
                '65f2dcb8-f2dc-4e93-acbf-505bc3889206'
                );
            """
        ).use {
            it.executeQuery().toList {
                Triple(
                    first = getString("id"),
                    second = getString("fellesformat"),
                    third = getString("msg_id"),
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
