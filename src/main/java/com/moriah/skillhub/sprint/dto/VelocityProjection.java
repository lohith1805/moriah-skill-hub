package com.moriah.skillhub.sprint.dto;

/** code-standards.md's own canonical example, verbatim — the JPQL constructor-expression record
 * {@link com.moriah.skillhub.sprint.repository.SprintRepository#findVelocity} projects into. */
public record VelocityProjection(
        Long sprintId,
        Integer sprintNumber,
        Integer plannedPoints,
        Integer completedPoints
) {
}
