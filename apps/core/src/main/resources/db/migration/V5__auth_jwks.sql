-- Better Auth JWT plugin key store (ADR-0017): the Ed25519 key pair that signs service tokens.
-- Web serves the public keys at /api/auth/jwks; core verifies against them. The private key is
-- encrypted by Better Auth with BETTER_AUTH_SECRET before it is stored.
CREATE TABLE auth_jwks (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    public_key   text        NOT NULL,
    private_key  text        NOT NULL,
    alg          text,
    crv          text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    expires_at   timestamptz
);
