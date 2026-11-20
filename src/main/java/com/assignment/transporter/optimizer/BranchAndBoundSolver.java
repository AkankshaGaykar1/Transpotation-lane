package com.assignment.transporter.optimizer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.IntStream;

import static com.assignment.transporter.optimizer.AssignmentProblem.NO_QUOTE;

// exact solver, depth first branch and bound over which transporters to pick.
// this is the p-median problem so it's NP-hard, hence the search. what keeps it small:
//  - a transporter that's the only bidder on a lane is fixed in from the start
//  - the greedy seed gives a good first answer to prune against
//  - lower bound: every lane at the cheaper of its current quote and the cheapest one still available deeper in
//  - upper bound on transporters used: picked so far plus picks left, never more than the biggest matching
//    of transporters to the lanes they quoted (UsageBound). a branch that can't beat the best on cost, or
//    tie it while using more transporters, gets cut
//  - a transporter that's dearer than the current choice on every lane it quotes is skipped, it can never win a lane
// the work budget is in lane evaluations (a node is one scan of the lanes, a full choice is whatever it takes to
// build its answer, tie sharing included). when it runs out we return the best so far as FEASIBLE, not OPTIMAL.
public final class BranchAndBoundSolver implements AssignmentSolver {

    private final AssignmentSolver seedSolver;
    private final long workBudget;

    // seedSolver gives the first answer to prune against
    public BranchAndBoundSolver(AssignmentSolver seedSolver, long workBudget) {
        if (workBudget < 1) {
            throw new IllegalArgumentException("workBudget must be at least 1");
        }
        this.seedSolver = Objects.requireNonNull(seedSolver, "seedSolver");
        this.workBudget = workBudget;
    }

    @Override
    public SolverResult solve(AssignmentProblem problem, int maxTransporters) {
        AssignmentSolver.requireValidLimit(maxTransporters);
        RequiredTransporters required = RequiredTransporters.of(problem);
        if (required.provesInfeasible(maxTransporters)) {
            return SolverResult.infeasible(0);
        }

        AssignmentSolution seed = seedSolver.solve(problem, maxTransporters).solution();
        Search search = new Search(problem, Math.min(maxTransporters, problem.transporterCount()), required,
                workBudget, seed);
        search.run();

        if (search.budgetExhausted) {
            return search.best == null ? SolverResult.unknown(search.nodes) : SolverResult.feasible(search.best, search.nodes);
        }
        return search.best == null ? SolverResult.infeasible(search.nodes) : SolverResult.optimal(search.best, search.nodes);
    }

    private static final class Search {

        private final AssignmentProblem problem;
        private final int limit;
        private final RequiredTransporters required;
        private final long workBudget;
        private final int maxUsable;
        private final int transporters;
        private final int lanes;
        private final int[] order;
        // suffixCheapest[d][lane] = cheapest quote for the lane among order[d..end]
        private final long[][] suffixCheapest;
        private final int[] chosen;

        private int chosenCount;
        private long work;
        private long nodes;
        private boolean budgetExhausted;
        private AssignmentSolution best;

        Search(AssignmentProblem problem, int limit, RequiredTransporters required, long workBudget,
               AssignmentSolution seed) {
            this.problem = problem;
            this.limit = limit;
            this.required = required;
            this.workBudget = workBudget;
            this.maxUsable = UsageBound.maxDistinctTransporters(problem);
            this.transporters = problem.transporterCount();
            this.lanes = problem.laneCount();
            this.order = searchOrder(problem, required);
            this.suffixCheapest = suffixCheapest(problem, order);
            this.chosen = new int[transporters];
            this.best = seed;
        }

        void run() {
            long[] cheapest = new long[lanes];
            Arrays.fill(cheapest, NO_QUOTE);
            visit(0, cheapest);
        }

        private void visit(int depth, long[] cheapest) {
            if (budgetExhausted) {
                return;
            }
            nodes++;
            work += lanes + 1;
            if (work > workBudget) {
                budgetExhausted = true;
                return;
            }

            boolean canAdd = chosenCount < limit && depth < transporters;
            long bound = 0;
            for (int lane = 0; lane < lanes; lane++) {
                long value = canAdd ? Math.min(cheapest[lane], suffixCheapest[depth][lane]) : cheapest[lane];
                if (value == NO_QUOTE) {
                    return; // this lane can't be covered on this branch
                }
                bound += value;
            }
            if (best != null) {
                if (bound > best.totalCost()) {
                    return;
                }
                int mostUsed = Math.min(maxUsable,
                        chosenCount + (canAdd ? Math.min(limit - chosenCount, transporters - depth) : 0));
                if (bound == best.totalCost() && mostUsed <= best.selectedTransporters().size()) {
                    return; // can't be cheaper and can't use more transporters
                }
            }

            if (!canAdd) {
                consider();
                return;
            }

            int transporter = order[depth];
            long[] nextCheapest = cheapest.clone();
            boolean canWinOrTieALane = false;
            for (int lane = 0; lane < lanes; lane++) {
                long cost = problem.cost(transporter, lane);
                if (cost == NO_QUOTE) {
                    continue;
                }
                if (cost <= nextCheapest[lane]) {
                    nextCheapest[lane] = cost;
                    canWinOrTieALane = true;
                }
            }
            if (canWinOrTieALane) {
                chosen[chosenCount++] = transporter;
                visit(depth + 1, nextCheapest);
                chosenCount--;
            }
            if (!required.isRequired(transporter)) {
                visit(depth + 1, cheapest);
            }
        }

        private void consider() {
            long[] spent = new long[1];
            AssignmentSolution.fromChosen(problem, Arrays.copyOf(chosen, chosenCount), spent)
                    .filter(candidate -> candidate.isBetterThan(best))
                    .ifPresent(candidate -> best = candidate);
            work += spent[0];
        }

        // required transporters first, then the ones that are cheapest on the most lanes
        private static int[] searchOrder(AssignmentProblem problem, RequiredTransporters required) {
            int[] cheapestLaneCount = new int[problem.transporterCount()];
            for (int lane = 0; lane < problem.laneCount(); lane++) {
                long cheapest = NO_QUOTE;
                for (int t = 0; t < problem.transporterCount(); t++) {
                    cheapest = Math.min(cheapest, problem.cost(t, lane));
                }
                for (int t = 0; t < problem.transporterCount(); t++) {
                    if (cheapest != NO_QUOTE && problem.cost(t, lane) == cheapest) {
                        cheapestLaneCount[t]++;
                    }
                }
            }
            return IntStream.range(0, problem.transporterCount()).boxed()
                    .sorted(Comparator.<Integer, Boolean>comparing(t -> !required.isRequired(t))
                            .thenComparing(t -> -cheapestLaneCount[t])
                            .thenComparing(t -> t))
                    .mapToInt(Integer::intValue)
                    .toArray();
        }

        private static long[][] suffixCheapest(AssignmentProblem problem, int[] order) {
            int lanes = problem.laneCount();
            long[][] suffix = new long[order.length + 1][lanes];
            Arrays.fill(suffix[order.length], NO_QUOTE);
            for (int depth = order.length - 1; depth >= 0; depth--) {
                for (int lane = 0; lane < lanes; lane++) {
                    suffix[depth][lane] = Math.min(suffix[depth + 1][lane], problem.cost(order[depth], lane));
                }
            }
            return suffix;
        }
    }
}
