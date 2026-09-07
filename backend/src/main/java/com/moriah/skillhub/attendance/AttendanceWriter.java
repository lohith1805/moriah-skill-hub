package com.moriah.skillhub.attendance;

import com.moriah.skillhub.attendance.entity.Attendance;
import com.moriah.skillhub.attendance.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * A separate bean, {@code REQUIRES_NEW}, deliberately — same reasoning {@code
 * TaskSubmissionWriter}'s own Javadoc documents (feature 12): catching a duplicate-key (or
 * deadlock) failure from a Hibernate-backed {@code save()} and continuing still leaves the
 * *session* poisoned the instant a flush fails, independent of whether the calling code catches
 * the translated exception. Self check-in's unique {@code (standup_id, user_id)} constraint is
 * the exact same shape of race {@code TaskSubmission}'s {@code (task_id, user_id,
 * attempt_number)} constraint has — build-plan.md feature 13: "double check-in is idempotent,
 * not an error" mirrors feature 12's "double-POST does not create two submissions" verbatim.
 */
@Service
@RequiredArgsConstructor
class AttendanceWriter {

    private final AttendanceRepository attendanceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Attendance tryInsert(Attendance attendance) {
        return attendanceRepository.saveAndFlush(attendance);
    }

    /** Also {@code REQUIRES_NEW} — MySQL/InnoDB's REPEATABLE READ snapshot is fixed at the outer
     * transaction's first read, so even a real-time read in the same transaction as an earlier
     * query can miss the winner's already-committed row (confirmed the hard way, feature 12). A
     * fresh transaction here gets a fresh snapshot, taken after the winner committed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<Attendance> findExisting(Long standupId, Long userId) {
        return attendanceRepository.findByStandupIdAndUserId(standupId, userId);
    }
}
