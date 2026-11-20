package com.assignment.transporter.optimizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.assignment.transporter.optimizer.AssignmentProblem.NO_QUOTE;

// one full answer: lane -> transporter, total cost, and which transporters ended up used
public record AssignmentSolution(long totalCost, Map<Long, Long> transporterByLane, List<Long> selectedTransporters) {

    public AssignmentSolution {
        if (totalCost < 0) {
            throw new IllegalArgumentException("totalCost must not be negative");
        }
        transporterByLane = Collections.unmodifiableMap(new TreeMap<>(transporterByLane));
        selectedTransporters = List.copyOf(selectedTransporters);
    }

    // builds the answer for a chosen set. every lane goes to the cheapest chosen quote, so the cost only depends
    // on the set. when several chosen transporters tie on a lane we share the tied lanes out (max matching) so as
    // many different transporters as possible get used, lower ids first. empty if some lane has no quote from the set.
    public static Optional<AssignmentSolution> fromChosen(AssignmentProblem problem, int[] chosenTransporterIndices) {
        return fromChosen(problem, chosenTransporterIndices, new long[1]);
    }

    // same thing, but adds the lane evaluations it used to work[0] so a budgeted search can charge for it
    public static Optional<AssignmentSolution> fromChosen(AssignmentProblem problem, int[] chosenTransporterIndices,
                                                          long[] work) {
        // index order is id order, so sorted indices are sorted ids
        int[] chosen = chosenTransporterIndices.clone();
        Arrays.sort(chosen);
        int lanes = problem.laneCount();
        work[0] += 2L * (chosen.length + 1) * lanes; // cheapest scan, tie scan, building the answer

        long[] cheapest = new long[lanes];
        Arrays.fill(cheapest, NO_QUOTE);
        for (int transporter : chosen) {
            for (int lane = 0; lane < lanes; lane++) {
                cheapest[lane] = Math.min(cheapest[lane], problem.cost(transporter, lane));
            }
        }
        long total = 0;
        for (long cost : cheapest) {
            if (cost == NO_QUOTE) {
                return Optional.empty();
            }
            total = Math.addExact(total, cost);
        }

        // default owner is the lowest id among the tied ones
        int[] owner = new int[lanes];
        boolean anyTie = false;
        int[] tied = new int[lanes];
        for (int transporter : chosen) {
            for (int lane = 0; lane < lanes; lane++) {
                if (problem.cost(transporter, lane) == cheapest[lane] && tied[lane]++ == 0) {
                    owner[lane] = transporter;
                }
            }
        }
        for (int count : tied) {
            anyTie |= count > 1;
        }
        if (anyTie) {
            spreadAcrossTransporters(problem, chosen, cheapest, owner, work);
        }

        Map<Long, Long> transporterByLane = new TreeMap<>();
        TreeSet<Long> used = new TreeSet<>();
        for (int lane = 0; lane < lanes; lane++) {
            long transporterId = problem.transporterId(owner[lane]);
            transporterByLane.put(problem.laneId(lane), transporterId);
            used.add(transporterId);
        }
        return Optional.of(new AssignmentSolution(total, transporterByLane, new ArrayList<>(used)));
    }

    // max matching of transporters to the distinct lanes they tie on (augmenting paths, transporters tried in id
    // order, which also gives the lowest ids). matched lanes go to their transporter, every other lane keeps its
    // lowest tied id, which is always one already in use or the matching could have grown.
    private static void spreadAcrossTransporters(AssignmentProblem problem, int[] chosen, long[] cheapest, int[] owner,
                                                 long[] work) {
        int lanes = cheapest.length;
        work[0] += (long) chosen.length * lanes;
        int[][] tiedLanes = new int[chosen.length][];
        for (int p = 0; p < chosen.length; p++) {
            int[] buffer = new int[lanes];
            int size = 0;
            for (int lane = 0; lane < lanes; lane++) {
                if (problem.cost(chosen[p], lane) == cheapest[lane]) {
                    buffer[size++] = lane;
                }
            }
            tiedLanes[p] = Arrays.copyOf(buffer, size);
        }

        int[] matchedTransporter = new int[lanes];
        Arrays.fill(matchedTransporter, -1);
        int[] visitedStamp = new int[lanes];
        for (int p = 0; p < chosen.length; p++) {
            augment(p, tiedLanes, matchedTransporter, visitedStamp, p + 1, work);
        }
        for (int lane = 0; lane < lanes; lane++) {
            if (matchedTransporter[lane] >= 0) {
                owner[lane] = chosen[matchedTransporter[lane]];
            }
        }
    }

    private static boolean augment(int transporter, int[][] tiedLanes, int[] matchedTransporter, int[] visitedStamp,
                                   int stamp, long[] work) {
        work[0] += 2L * tiedLanes[transporter].length;
        for (int lane : tiedLanes[transporter]) {
            if (matchedTransporter[lane] < 0) {
                matchedTransporter[lane] = transporter;
                return true;
            }
        }
        for (int lane : tiedLanes[transporter]) {
            if (visitedStamp[lane] == stamp) {
                continue;
            }
            visitedStamp[lane] = stamp;
            if (augment(matchedTransporter[lane], tiedLanes, matchedTransporter, visitedStamp, stamp, work)) {
                matchedTransporter[lane] = transporter;
                return true;
            }
        }
        return false;
    }

    // lower cost wins, then more transporters used, then the smaller list of ids
    public boolean isBetterThan(AssignmentSolution other) {
        if (other == null) {
            return true;
        }
        if (totalCost != other.totalCost) {
            return totalCost < other.totalCost;
        }
        int mine = selectedTransporters.size();
        int theirs = other.selectedTransporters.size();
        if (mine != theirs) {
            return mine > theirs;
        }
        for (int i = 0; i < mine; i++) {
            int comparison = Long.compare(selectedTransporters.get(i), other.selectedTransporters.get(i));
            if (comparison != 0) {
                return comparison < 0;
            }
        }
        return false;
    }
}
