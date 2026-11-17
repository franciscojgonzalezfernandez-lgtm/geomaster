package com.geomaster.core.auth;

import java.security.PublicKey;
import java.util.Optional;

/** Resolves the Ed25519 public key for a JWS {@code kid}. */
@FunctionalInterface
public interface SigningKeys {
    Optional<PublicKey> find(String kid);
}
