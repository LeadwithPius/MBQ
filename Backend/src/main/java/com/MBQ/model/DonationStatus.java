package com.MBQ.model;

/** COLLECTED -> PASSED (becomes a stock batch) or FAILED (discarded). Both outcomes are final. */
public enum DonationStatus {
    COLLECTED,
    PASSED,
    FAILED
}
