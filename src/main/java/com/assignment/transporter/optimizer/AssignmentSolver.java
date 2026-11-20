package com.assignment.transporter.optimizer;

// picks at most maxTransporters and gives every lane to one of them, as cheaply as possible.
// rules every implementation follows (see AssignmentSolution):
//  - a lane goes to a chosen transporter that quoted it
//  - the cheapest chosen quote wins, a tie goes to the lower id
//  - lowest total cost, then more transporters used, then the smaller id list
public interface AssignmentSolver {

    SolverResult solve(AssignmentProblem problem, int maxTransporters);

    static void requireValidLimit(int maxTransporters) {
        if (maxTransporters < 1) {
            throw new IllegalArgumentException("maxTransporters must be at least 1, got " + maxTransporters);
        }
    }
}
