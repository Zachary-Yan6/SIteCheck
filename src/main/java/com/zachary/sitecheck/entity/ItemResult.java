package com.zachary.sitecheck.entity;

/** Outcome of one checklist item (FR-11); stored as text in {@code inspection_results.result}. */
public enum ItemResult {
    PASS,
    FAIL,
    /** Not applicable. */
    NA
}
