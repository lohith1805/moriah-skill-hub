package com.moriah.skillhub.batch.dto;

import java.time.Instant;

/** {@code (batchId, userId, joinedAt)} for every {@code ACTIVE} or {@code ON_PIP} {@code
 * batch_students} row — {@code BatchService#activeAndOnPipEnrollments}'s bulk read, backing {@code
 * StudentMetricsService}'s whole-cohort refresh (feature 16) without that job touching {@code
 * BatchStudentRepository}/{@code BatchStudent} directly, the same cross-package boundary {@link
 * ActiveMemberProjection} already established for feature 13. {@code ON_PIP} is included alongside
 * {@code ACTIVE} deliberately: a student already flagged still needs live metrics computed every
 * night — {@code PipEvaluationJob}'s clearance check (feature 17) reads task-completion/review
 * outcomes from AFTER the flag was raised, not a snapshot frozen at the moment they were placed on
 * PIP. */
public record ActiveEnrollmentProjection(Long batchId, Long userId, Instant joinedAt) {
}
