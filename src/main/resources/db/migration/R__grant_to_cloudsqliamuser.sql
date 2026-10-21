-- Endre denne filen (f.eks. kommentaren) når nye tabeller legges til, slik at Flyway kjører den på nytt.
-- Sist endret for: DIALOGMELDINGVEDLEGG (V6_3)
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM cloudsqliamuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO "isyfo-analyse";
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
