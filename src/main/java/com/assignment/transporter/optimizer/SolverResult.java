package com.assignment.transporter.optimizer;

import java.util.Objects;
import java.util.Optional;

// what a solver found:
//  OPTIMAL    - search finished, nothing better exists
//  FEASIBLE   - valid answer, but the search stopped early so a better one may exist
//  INFEASIBLE - proven that no set within the limit covers every lane
//  UNKNOWN    - search stopped early without finding any valid answer
public record SolverResult(Status status, AssignmentSolution solution, long nodesExplored) {

    public enum Status { OPTIMAL, FEASIBLE, INFEASIBLE, UNKNOWN }

    public SolverResult {
        Objects.requireNonNull(status, "status");
        boolean needsSolution = status == Status.OPTIMAL || status == Status.FEASIBLE;
        if (needsSolution != (solution != null)) {
            throw new IllegalArgumentException("Status " + status + (needsSolution ? " needs" : " must not have")
                    + " a solution");
        }
    }

    public static SolverResult optimal(AssignmentSolution solution, long nodesExplored) {
        return new SolverResult(Status.OPTIMAL, solution, nodesExplored);
    }

    public static SolverResult feasible(AssignmentSolution solution, long nodesExplored) {
        return new SolverResult(Status.FEASIBLE, solution, nodesExplored);
    }

    public static SolverResult infeasible(long nodesExplored) {
        return new SolverResult(Status.INFEASIBLE, null, nodesExplored);
    }

    public static SolverResult unknown(long nodesExplored) {
        return new SolverResult(Status.UNKNOWN, null, nodesExplored);
    }

    public Optional<AssignmentSolution> solutionIfAny() {
        return Optional.ofNullable(solution);
    }
}
