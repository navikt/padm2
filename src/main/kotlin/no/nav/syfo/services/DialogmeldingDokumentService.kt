package no.nav.syfo.services

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.persistering.db.hasSavedDialogmeldingDokument

fun dialogmeldingDokumentWithShaExists(
    sha256String: String,
    database: DatabaseInterface,
): Boolean {
    return database.connection.hasSavedDialogmeldingDokument(sha256String)
}
