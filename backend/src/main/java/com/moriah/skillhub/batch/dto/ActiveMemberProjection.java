package com.moriah.skillhub.batch.dto;

/** {@code (batchId, userId)} pair for an {@code ACTIVE} {@code batch_students} row — {@code
 * BatchService#activeMembersOf}'s bulk read, backing {@code AttendanceFinalisationJob}'s
 * enrolled-student anti-join (feature 13) without that job touching {@code
 * BatchStudentRepository}/{@code BatchStudent} directly (architecture.md: "a feature module may
 * call another module's service interface, never its repository or entity"). */
public record ActiveMemberProjection(Long batchId, Long userId) {
}
