package no.nav.syfo.services

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.persistering.db.hasSavedDialogmeldingDokument

fun dialogmeldingDokumentWithShaExists(
    dialogmeldingId: String,
    sha256String: String,
    database: DatabaseInterface,
): Boolean {
    return database.hasSavedDialogmeldingDokument(dialogmeldingId, sha256String)
}
