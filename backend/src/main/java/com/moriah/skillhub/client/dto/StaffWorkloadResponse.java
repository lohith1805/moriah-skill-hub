package com.moriah.skillhub.client.dto;

/**
 * One BA/developer's current client-project workload, for the admin Client Project Assignments
 * screen. {@code openProjectCount} is how many currently-{@code SUBMITTED}/{@code IN_PROGRESS}
 * client projects are assigned to them right now; {@code label} is the same count bucketed into
 * "Available" / "Busy" / "High workload" (see {@code Constants.STAFF_WORKLOAD_*}).
 */
public record StaffWorkloadResponse(
        String userUuid,
        String fullName,
        int openProjectCount,
        String label
) {
}
