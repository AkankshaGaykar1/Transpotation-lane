package com.assignment.transporter.service.exception;

import java.util.List;

public class InfeasibleAssignmentException extends RuntimeException {

    private final transient List<String> details;

    public InfeasibleAssignmentException(String message, List<String> details) {
        super(message);
        this.details = List.copyOf(details);
    }

    public List<String> details() {
        return details;
    }
}
