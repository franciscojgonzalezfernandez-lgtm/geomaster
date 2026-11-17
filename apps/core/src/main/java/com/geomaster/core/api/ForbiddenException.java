package com.geomaster.core.api;

/** The caller belongs to the account but lacks the role for this action (e.g. only owners delete). */
public final class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message, null, false, false);
    }
}
