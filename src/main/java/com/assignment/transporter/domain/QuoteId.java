package com.assignment.transporter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// one quote per (transporter, lane)
@Embeddable
public record QuoteId(@Column(name = "transporter_id") Long transporterId, @Column(name = "lane_id") Long laneId) {
}
