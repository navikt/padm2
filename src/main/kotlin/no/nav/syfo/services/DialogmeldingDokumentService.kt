package no.nav.syfo.services

import no.nav.syfo.db.Database
import no.nav.syfo.persistering.db.hasSavedDialogmeldingDokument

fun dialogmeldingDokumentWithShaExists(
    sha256String: String,
    database: Database
): Boolean {
    return database.connection.hasSavedDialogmeldingDokument(sha256String)
}
