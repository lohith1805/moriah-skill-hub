package com.moriah.skillhub.client.entity;

/** No enumerated value set exists in architecture.md for {@code client_projects.status} — this
 * build's own minimal closed set. {@code SUBMITTED} is the only value {@code POST
 * /api/v1/clients/projects} (this feature's one project-creation endpoint) ever sets — there is
 * no "start delivery" or "mark complete" endpoint anywhere in build-plan.md's feature 21 endpoint
 * list. {@code IN_PROGRESS}/{@code COMPLETED} are left in schema for a future delivery-tracking
 * feature with no route yet, the same "table exists, not fully API-managed yet" treatment {@code
 * com.moriah.skillhub.hr.entity.PayrollStatus} already establishes for {@code DRAFT}/{@code
 * PAID}. */
public enum ClientProjectStatus {
    SUBMITTED,
    IN_PROGRESS,
    COMPLETED
}
