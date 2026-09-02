package com.moriah.skillhub.common.notification.repository;

import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationStatus;
import com.moriah.skillhub.common.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** {@code NotificationReaperJob}'s DB-vs-Redis reconciliation — see its Javadoc. Finds rows
     * that never got a Redis entry at all (or lost one), not the ones the reaper's own Redis-side
     * staleness sweep already handles. */
    List<Notification> findByStatusAndCreatedAtBefore(NotificationStatus status, Instant createdBefore);

    /** The in-app feed (gap B1.5) — a caller's own {@code IN_APP} rows, newest first. The
     * {@code Pageable}'s sort is supplied by the controller; callers never see another user's
     * rows because {@code userId} is always the resolved {@code @CurrentUser}. */
    Page<Notification> findByUserIdAndChannel(Long userId, NotificationChannel channel, Pageable pageable);

    Page<Notification> findByUserIdAndChannelAndReadAtIsNull(
            Long userId, NotificationChannel channel, Pageable pageable);

    long countByUserIdAndChannelAndReadAtIsNull(Long userId, NotificationChannel channel);

    /** Ownership-scoped lookup for {@code PUT /notifications/{id}/read} — an id that belongs to
     * another user resolves to empty (a 404), never someone else's row. */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** {@code PUT /notifications/read-all} — one statement, no per-row load. Returns the number of
     * rows flipped from unread to read. */
    @Modifying
    @Query("""
            UPDATE Notification n SET n.readAt = :now
             WHERE n.userId = :userId AND n.channel = com.moriah.skillhub.common.notification.NotificationChannel.IN_APP
               AND n.readAt IS NULL
            """)
    int markAllReadForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
