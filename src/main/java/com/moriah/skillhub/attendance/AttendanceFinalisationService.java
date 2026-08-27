package com.moriah.skillhub.attendance;

import com.moriah.skillhub.attendance.dto.AttendanceKeyProjection;
import com.moriah.skillhub.attendance.entity.Attendance;
import com.moriah.skillhub.attendance.entity.AttendanceStatus;
import com.moriah.skillhub.attendance.entity.Standup;
import com.moriah.skillhub.attendance.entity.StandupStatus;
import com.moriah.skillhub.attendance.repository.AttendanceRepository;
import com.moriah.skillhub.attendance.repository.StandupRepository;
import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.dto.ActiveMemberProjection;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A separate bean from {@link AttendanceFinalisationJob}, deliberately — code-standards.md
 * "Transactions": "Never rely on {@code @Transactional} on a private or self-invoked method — it
 * does nothing." {@code AttendanceFinalisationJob.finaliseScheduled()} is itself a {@code
 * @Scheduled} method invoked by Spring's scheduler directly on the target bean, not through the
 * transactional proxy; a same-class call from there to {@link #finalise} would be exactly that
 * self-invocation, silently running every repository write in its own auto-committing
 * mini-transaction instead of the one atomic unit the method's own contract (see below) promises.
 * Calling {@link #finalise} on this separate bean instead goes through Spring's proxy like any
 * other cross-bean call, so {@code @Transactional} actually applies. Same fix, same reasoning as
 * {@code RefreshTokenRevocationService} (feature 03/04's own version of this bug) and {@code
 * EntitlementFlagsLoader} (the {@code @Cacheable} equivalent) — see progress-tracker.md.
 */
@Service
@RequiredArgsConstructor
public class AttendanceFinalisationService {

    private final StandupRepository standupRepository;
    private final AttendanceRepository attendanceRepository;
    private final BatchService batchService;
    private final UserRepository userRepository;

    /** Every data source is one flat query — {@link StandupRepository#findEligibleForFinalisation},
     * {@link BatchService#activeMembersOf}, {@link AttendanceRepository#findKeysByStandupIds} —
     * with the anti-join computed in memory (AGENTS.md: "a repository call inside the per-item
     * loop is a defect"). Re-running is a genuine no-op: every standup this run touches gets
     * {@code finalisedAt} set in the same pass, so the next run's own eligibility query excludes
     * it. That no-op guarantee — and the "write every absent row for a standup, or none of them"
     * atomicity implied by the class Javadoc's own promises — only holds when this whole method
     * commits or rolls back as one unit, which is exactly what running through the proxy (see the
     * class Javadoc) restores. */
    @Transactional
    public int finalise() {
        Instant now = Instant.now();
        List<Standup> eligible = standupRepository.findEligibleForFinalisation(now);
        if (eligible.isEmpty()) {
            return 0;
        }

        Set<Long> batchIds = eligible.stream().map(s -> s.getBatch().getId()).collect(Collectors.toSet());
        Map<Long, List<Long>> activeUserIdsByBatch = batchService.activeMembersOf(batchIds).stream()
                .collect(Collectors.groupingBy(ActiveMemberProjection::batchId,
                        Collectors.mapping(ActiveMemberProjection::userId, Collectors.toList())));

        List<Long> standupIds = eligible.stream().map(Standup::getId).toList();
        Set<AttendanceKeyProjection> alreadyHasRow = new HashSet<>(attendanceRepository.findKeysByStandupIds(standupIds));

        List<Attendance> absentRows = new ArrayList<>();
        for (Standup standup : eligible) {
            List<Long> activeUserIds = activeUserIdsByBatch.getOrDefault(standup.getBatch().getId(), List.of());
            for (Long userId : activeUserIds) {
                if (alreadyHasRow.contains(new AttendanceKeyProjection(standup.getId(), userId))) {
                    continue;
                }
                Attendance absent = new Attendance();
                absent.setStandup(standup);
                // getReferenceById, not findById — a proxy is enough to set the FK, and avoids
                // one SELECT per absent student on top of the two flat queries above.
                User userRef = userRepository.getReferenceById(userId);
                absent.setUser(userRef);
                absent.setStatus(AttendanceStatus.ABSENT);
                absent.setAutoMarked(true);
                absentRows.add(absent);
            }

            if (standup.getStatus() == StandupStatus.SCHEDULED) {
                standup.setStatus(StandupStatus.CONDUCTED);
            }
            standup.setFinalisedAt(now);
        }

        attendanceRepository.saveAll(absentRows);
        standupRepository.saveAll(eligible);

        return eligible.size();
    }
}
