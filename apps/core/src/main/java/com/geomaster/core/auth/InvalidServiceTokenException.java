package com.geomaster.core.auth;

/** A service token was missing, malformed, unsigned by a known key, expired or otherwise invalid. */
public final class InvalidServiceTokenException extends RuntimeException {
    public InvalidServiceTokenException(String reason) {
        super(reason, null, false, false);
    }
}
