package no.nav.syfo.persistering.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.Dialogmelding
import no.nav.syfo.model.ReceivedDialogmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.domain.DialogmeldingTidspunkt
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp

fun DatabaseInterface.lagreMottattDialogmelding(
    receivedDialogmelding: ReceivedDialogmelding,
    validationResult: ValidationResult,
    sha256String: String,
) {
    connection.use { connection ->
        connection.opprettDialogmeldingOpplysninger(receivedDialogmelding)
        connection.opprettDialogmeldingDokument(receivedDialogmelding.dialogmelding, sha256String)
        connection.opprettBehandlingsutfall(
            validationResult, receivedDialogmelding.dialogmelding.id
        )
        connection.commit()
    }
}
fun ResultSet.toDialogmeldingTidspunkt(): DialogmeldingTidspunkt =
    DialogmeldingTidspunkt(
        signaturDato = getString("signaturDato"),
        mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
    )

private fun Connection.opprettDialogmeldingOpplysninger(receivedDialogmelding: ReceivedDialogmelding) {
    this.prepareStatement(
        """
            INSERT INTO DIALOGMELDINGOPPLYSNINGER(
                id,
                pasient_fnr,
                pasient_aktoer_id,
                lege_fnr,
                lege_aktoer_id,
                mottak_id,
                msg_id,
                legekontor_org_nr,
                legekontor_her_id,
                legekontor_resh_id,
                mottatt_tidspunkt,
                tss_id
                )
            VALUES  (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
    ).use {
        it.setString(1, receivedDialogmelding.dialogmelding.id)
        it.setString(2, receivedDialogmelding.personNrPasient)
        it.setString(3, receivedDialogmelding.pasientAktoerId)
        it.setString(4, receivedDialogmelding.personNrLege)
        it.setString(5, receivedDialogmelding.legeAktoerId)
        it.setString(6, receivedDialogmelding.navLogId)
        it.setString(7, receivedDialogmelding.msgId)
        it.setString(8, receivedDialogmelding.legekontorOrgNr)
        it.setString(9, receivedDialogmelding.legekontorHerId)
        it.setString(10, receivedDialogmelding.legekontorReshId)
        it.setTimestamp(11, Timestamp.valueOf(receivedDialogmelding.mottattDato))
        it.setString(12, receivedDialogmelding.tssid)
        it.executeUpdate()
    }
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

private fun Connection.opprettBehandlingsutfall(validationResult: ValidationResult, dialogmeldingid: String) {
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

fun Connection.erDialogmeldingOpplysningerLagret(dialogmeldingid: String) =
    use { connection ->
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

fun Connection.hentMottattTidspunkt(shaString: String) =
    use { connection ->
        connection.prepareStatement(
            """
                SELECT dok.dialogmelding -> 'signaturDato' AS signaturDato, o.mottatt_tidspunkt
                FROM dialogmeldingdokument dok
                INNER JOIN dialogmeldingopplysninger o ON o.id = dok.id
                WHERE dok.sha_string=?
                """
        ).use {
            it.setString(1, shaString)
            it.executeQuery().toList { toDialogmeldingTidspunkt() }.first()
        }
    }

fun Connection.hasSavedDialogmeldingDokument(shaString: String): Boolean =
    use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM DIALOGMELDINGDOKUMENT
                WHERE sha_string=?;
                """
        ).use {
            it.setString(1, shaString)
            it.executeQuery().next()
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
