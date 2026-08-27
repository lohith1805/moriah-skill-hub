package com.moriah.skillhub.batch.dto;

import java.time.Instant;

/** {@code BatchService#graduate}'s return shape — deliberately not the {@code BatchStudent}
 * entity itself (architecture.md layer rule: a feature module may never touch another module's
 * entity directly), and deliberately not {@code certificate.dto.GraduationResponse} either, since
 * {@code batch/} shouldn't own a DTO shape that belongs to a sibling feature. {@code
 * certificate.GraduationService} composes the final response from this plus {@code
 * BatchService#get}'s own {@code BatchResponse} — same cross-package-projection pattern {@code
 * ActiveMemberProjection}/{@code StudentMetricProjection} already established. */
public record GraduationResult(Long userId, String userFullName, Instant graduatedAt) {
}
