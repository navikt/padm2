package no.nav.syfo.kafka.helsemelding

/**
 * Innkommende dialogmelding fra Team Helsemelding (topic helsemelding.dialog.in).
 * Speiler IncomingDialogMessage (schema version 1) i navikt/helsemelding-json-schema.
 */
data class IncomingDialogMessage(
    val version: Int,
    val id: String,
    val type: IncomingDialogMessageType,
    val receivedAt: String,
    val patientIdent: String,
    val conversationReference: ConversationReference?,
    val message: String?,
    val numberOfAttachments: Int,
    val provider: Provider,
    val signature: Signature,
    val documentId: String,
)

data class ConversationReference(
    val parentMessageId: String,
    val conversationId: String,
)

data class Provider(
    val ident: String,
    val hprNumber: String?,
    val office: ProviderOffice,
)

data class ProviderOffice(
    val orgNumber: String?,
    val orgName: String,
    val herId: String?,
)

data class Signature(
    val signingProviderIdent: String,
    val signedAt: String,
)

enum class IncomingType {
    DIALOG_NOTE,
    DIALOG_RESPONSE,
}

enum class IncomingDialogMessageType(
    val messageType: IncomingType,
    val codeSystem: Int,
    val code: Int,
) {
    ACCEPTS_MEETING_INVITATION(IncomingType.DIALOG_RESPONSE, 8126, 1),
    REQUESTS_NEW_MEETING_TIME(IncomingType.DIALOG_RESPONSE, 8126, 2),
    DECLINES_MEETING_WITH_REASON(IncomingType.DIALOG_RESPONSE, 8126, 3),
    PATIENT_REQUEST_RESPONSE(IncomingType.DIALOG_RESPONSE, 9069, 5),
    SICK_LEAVE_FOLLOW_UP_INQUIRY(IncomingType.DIALOG_NOTE, 8128, 1),
    PATIENT_INQUIRY(IncomingType.DIALOG_NOTE, 8128, 2),
}
