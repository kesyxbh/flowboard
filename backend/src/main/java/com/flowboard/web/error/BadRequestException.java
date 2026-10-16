package com.flowboard.web.error;

/** Well-formed input that the business rules do not allow, for example moving a card to another board. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
