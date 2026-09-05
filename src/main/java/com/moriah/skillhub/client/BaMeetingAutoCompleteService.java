package com.moriah.skillhub.client;

import com.moriah.skillhub.client.entity.BaMeeting;
import com.moriah.skillhub.client.entity.BaMeetingStatus;
import com.moriah.skillhub.client.repository.BaMeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A separate bean from {@link BaMeetingAutoCompleteJob}, deliberately — same "{@code
 * @Transactional} on a {@code @Scheduled} method invoked directly by Spring's scheduler is a
 * self-invocation and does nothing" reasoning {@code QuizAttemptExpiryService}'s own Javadoc
 * documents (code-standards.md "Transactions").
 * <p>
 * The user's own ask: once a meeting's scheduled time is over, it should stop looking like it's
 * still coming up. {@code durationMinutes} is nullable at the DB level (a meeting created before
 * this field was required, or one that genuinely omitted it) — treated as 60 minutes for this
 * check only, the same default the frontend itself falls back to when scheduling.
 */
@Service
@RequiredArgsConstructor
public class BaMeetingAutoCompleteService {

    private static final int DEFAULT_DURATION_MINUTES = 60;

    private final BaMeetingRepository meetingRepository;

    @Transactional
    public int autoComplete() {
        Instant now = Instant.now();
        List<BaMeeting> scheduled = meetingRepository.findByStatus(BaMeetingStatus.SCHEDULED);
        List<BaMeeting> due = scheduled.stream()
                .filter(m -> m.getScheduledAt()
                        .plus(Duration.ofMinutes(m.getDurationMinutes() != null ? m.getDurationMinutes() : DEFAULT_DURATION_MINUTES))
                        .isBefore(now))
                .toList();
        if (due.isEmpty()) {
            return 0;
        }
        due.forEach(m -> m.setStatus(BaMeetingStatus.COMPLETED));
        meetingRepository.saveAll(due);
        return due.size();
    }
}
