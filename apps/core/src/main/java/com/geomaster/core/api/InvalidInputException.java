package com.geomaster.core.api;

/** A request field failed validation; mapped to 422 naming the field (REQ-8, REQ-9). */
public final class InvalidInputException extends RuntimeException {

    private final String field;

    public InvalidInputException(String field, String message) {
        super(message, null, false, false);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
