package com.hospital.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when submitted data or a business rule is not acceptable.
 * The messages are meant to be shown to the receptionist or doctor on the web page.
 */
public class ValidationException extends Exception {

    private static final long serialVersionUID = 1L;

    private final List<String> errors;

    public ValidationException(String message) {
        super(message);
        this.errors = Collections.singletonList(message);
    }

    public ValidationException(List<String> errors) {
        super(String.join(" ", errors));
        this.errors = new ArrayList<>(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
