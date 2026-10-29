package no.nav.syfo.kafka.helsemelding

fun generateIncomingDialogMessageJson(
    id: String = "6a8c0c7e-2b7f-4a8e-9d1c-3f1b2e4d5a60",
    type: IncomingDialogMessageType = IncomingDialogMessageType.PATIENT_INQUIRY,
    message: String? = "Henvendelse fra lege",
    conversationReference: Boolean = true,
    numberOfAttachments: Int = 1,
): String =
    """
    {
        "version": "1",
        "id": "$id",
        "type": "${type.name}",
        "receivedAt": "2026-06-03T12:00:00Z",
        "patientIdent": "01010112345",
        "conversationReference": ${if (conversationReference) """{"parentMessageId": "parent-message-id", "conversationId": "conversation-id"}""" else "null"},
        "message": ${message?.let { "\"$it\"" } ?: "null"},
        "numberOfAttachments": $numberOfAttachments,
        "provider": {
            "ident": "02020212345",
            "hprNumber": "987654321",
            "office": {
                "orgNumber": "223456789",
                "orgName": "Kule helsetjenester AS",
                "herId": "1234567"
            }
        },
        "signature": {
            "signingProviderIdent": "03030312345",
            "signedAt": "2026-06-03T11:59:00Z"
        },
        "documentId": "OD2503016631907"
    }
    """.trimIndent()
