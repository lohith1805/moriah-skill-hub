package com.moriah.skillhub.common.notification.repository;

import com.moriah.skillhub.common.notification.NotificationStatus;
import com.moriah.skillhub.common.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** {@code NotificationReaperJob}'s DB-vs-Redis reconciliation — see its Javadoc. Finds rows
     * that never got a Redis entry at all (or lost one), not the ones the reaper's own Redis-side
     * staleness sweep already handles. */
    List<Notification> findByStatusAndCreatedAtBefore(NotificationStatus status, Instant createdBefore);
}
