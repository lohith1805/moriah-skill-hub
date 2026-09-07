package com.moriah.skillhub.client.entity;

/** No enumerated value set exists in architecture.md for {@code clients.status} — this build's
 * own minimal closed set. {@code ACTIVE} is the only value {@code POST /api/v1/clients} (this
 * feature's one client-creation endpoint) ever sets; {@code INACTIVE} exists in schema for a
 * future admin deactivation workflow with no route in build-plan.md's feature 21 endpoint list —
 * the same "table exists, not fully API-managed yet" treatment {@code
 * com.moriah.skillhub.hr.entity.PayrollStatus} already establishes for its own {@code DRAFT}/
 * {@code PAID} values. */
public enum ClientStatus {
    ACTIVE,
    INACTIVE
}
