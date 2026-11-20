package com.assignment.transporter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// bound from transporter.* in application.properties
@ConfigurationProperties(prefix = "transporter")
public record TransporterProperties(@DefaultValue Solver solver, @DefaultValue Limits limits) {

    // all three are counted in lane evaluations, so the run time doesn't depend on the shape of the input
    // workBudget: the exact search, seedWorkBudget: the greedy seed, coverageWorkBudget: working out
    // how many transporters a failed request would have needed
    public record Solver(@DefaultValue("25000000") long workBudget, @DefaultValue("10000000") long seedWorkBudget,
                         @DefaultValue("20000000") long coverageWorkBudget) {
    }

    // size caps so a small server doesn't fall over on a huge payload
    public record Limits(@DefaultValue("2000") int maxLanes, @DefaultValue("200") int maxTransporters,
                         @DefaultValue("10000") int maxQuotes, @DefaultValue("200000") long maxMatrixCells,
                         @DefaultValue("1048576") long maxBodyBytes) {
    }
}
