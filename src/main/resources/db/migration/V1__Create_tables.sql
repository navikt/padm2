CREATE TABLE DIALOGMELDINGOPPLYSNINGER
(
    id                 VARCHAR(64) PRIMARY KEY,
    pasient_fnr        VARCHAR(11) NOT NULL,
    pasient_aktoer_id  VARCHAR(63) NOT NULL,
    lege_fnr           VARCHAR(11) NOT NULL,
    lege_aktoer_id     VARCHAR(63) NOT NULL,
    mottak_id          VARCHAR(63) NOT NULL,
    msg_id             VARCHAR(63) NOT NULL,
    legekontor_org_nr  VARCHAR(20),
    legekontor_her_id  VARCHAR(63),
    legekontor_resh_id VARCHAR(63),
    mottatt_tidspunkt  TIMESTAMP   NOT NULL,
    tss_id             VARCHAR(63)
);

CREATE TABLE DIALOGMELDINGDOKUMENT
(
    id         VARCHAR(64) PRIMARY KEY REFERENCES DIALOGMELDINGOPPLYSNINGER ON DELETE CASCADE,
    dialogmleding JSONB NOT NULL
);

CREATE TABLE BEHANDLINGSUTFALL
(
    id                VARCHAR(64) PRIMARY KEY REFERENCES DIALOGMELDINGOPPLYSNINGER ON DELETE CASCADE,
    behandlingsutfall JSONB NOT NULL
);
