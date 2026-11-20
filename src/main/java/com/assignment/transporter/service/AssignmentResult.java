package com.assignment.transporter.service;

import com.assignment.transporter.optimizer.AssignmentSolution;

// optimal: the search finished, so nothing cheaper exists
public record AssignmentResult(AssignmentSolution solution, boolean optimal) {
}
