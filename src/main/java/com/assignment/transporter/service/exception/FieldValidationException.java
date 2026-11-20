package com.assignment.transporter.service.exception;

import java.util.List;

// the per-field checks on the dtos failed
public class FieldValidationException extends RuntimeException {

    private final transient List<String> errors;

    public FieldValidationException(List<String> errors) {
        super("The request is not valid.");
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
