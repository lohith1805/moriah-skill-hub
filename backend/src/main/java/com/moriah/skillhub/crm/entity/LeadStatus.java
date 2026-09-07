package com.moriah.skillhub.crm.entity;

/**
 * build-plan.md feature 18: {@code NEW -> CONTACTED -> DEMO_SCHEDULED -> COUNSELLING_DONE ->
 * PAYMENT_PENDING -> ENROLLED | LOST}. Declaration order is the pipeline order — {@link
 * com.moriah.skillhub.crm.LeadService} relies on {@link #ordinal()} to detect a forward skip vs a
 * one-step advance vs a backward move. {@code LOST} is a terminal exit reachable from any
 * non-terminal status, not part of the forward sequence itself.
 */
public enum LeadStatus {
    NEW,
    CONTACTED,
    DEMO_SCHEDULED,
    COUNSELLING_DONE,
    PAYMENT_PENDING,
    ENROLLED,
    LOST
}
