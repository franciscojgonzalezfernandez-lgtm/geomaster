package com.geomaster.core.auth;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

/** Converts a raw 32-byte Ed25519 public key (JWK {@code x}, RFC 8037) into a JDK {@link PublicKey}. */
final class Ed25519Keys {

    /** DER prefix of SubjectPublicKeyInfo for id-Ed25519 (OID 1.3.101.112), followed by the raw key. */
    private static final byte[] X509_PREFIX = {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};

    private Ed25519Keys() {}

    static PublicKey fromRaw(byte[] raw) throws GeneralSecurityException {
        if (raw.length != 32) {
            throw new GeneralSecurityException("Ed25519 public key must be 32 bytes, got " + raw.length);
        }
        byte[] der = new byte[X509_PREFIX.length + raw.length];
        System.arraycopy(X509_PREFIX, 0, der, 0, X509_PREFIX.length);
        System.arraycopy(raw, 0, der, X509_PREFIX.length, raw.length);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
    }
}
