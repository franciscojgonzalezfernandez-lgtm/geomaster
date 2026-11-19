-- Better Auth's account model (our auth_identity) always includes a `password` field and refuses
-- to start when the column is missing, even with email/password sign-in disabled. GEOMASTER only
-- uses magic links and Google (ADR-0017), so this column stays NULL.
ALTER TABLE auth_identity ADD COLUMN password text;
