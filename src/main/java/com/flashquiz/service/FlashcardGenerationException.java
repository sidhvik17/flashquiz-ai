package com.flashquiz.service;

public class FlashcardGenerationException extends RuntimeException {

    public enum Reason {
        NOT_CONFIGURED,
        TIMEOUT,
        UPSTREAM_ERROR,
        BAD_RESPONSE
    }

    private final Reason reason;

    public FlashcardGenerationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public FlashcardGenerationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
