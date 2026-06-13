package com.nicko.verapay.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidReversalException extends RuntimeException {
    public InvalidReversalException(String message) {
        super(message);
    }
}