package com.assignment.transporter.service.exception;

public class SearchLimitReachedException extends RuntimeException {

    public SearchLimitReachedException(int maxTransporters) {
        super("The search budget ran out before a valid assignment with at most " + maxTransporters
                + " transporters was found. Try a larger limit or a smaller input.");
    }
}
