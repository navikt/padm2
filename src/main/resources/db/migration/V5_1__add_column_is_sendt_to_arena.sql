ALTER TABLE DIALOGMELDINGOPPLYSNINGER
    ADD COLUMN is_sent_to_arena BOOLEAN DEFAULT FALSE;

UPDATE dialogmeldingopplysninger
SET is_sent_to_arena = TRUE
WHERE arena IS NOT NULL;
