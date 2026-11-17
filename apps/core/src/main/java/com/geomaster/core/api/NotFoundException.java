package com.geomaster.core.api;

/** The resource does not exist for the current account (REQ-7: never reveals another tenant's data). */
public final class NotFoundException extends RuntimeException {
    public NotFoundException(String what) {
        super(what + " not found", null, false, false);
    }
}
