package com.flowboard.web.error;

/** The request conflicts with existing data, for example an e-mail address that is already registered. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
