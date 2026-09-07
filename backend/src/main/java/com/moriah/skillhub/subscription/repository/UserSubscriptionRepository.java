package com.moriah.skillhub.subscription.repository;

import com.moriah.skillhub.subscription.entity.SubscriptionStatus;
import com.moriah.skillhub.subscription.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    /** V3's {@code uq_one_active_subscription} generated column guarantees at most one row can
     * ever match this — a {@code List} would never legitimately hold more than one element, but
     * this reads more honestly as "the one active subscription, if any" than a fragile
     * single-result query that would throw on a constraint violation that should be impossible. */
    Optional<UserSubscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);

    /** {@code SubscriptionExpiryJob} — one flat query for the whole cohort, per code-standards.md
     * "Async and Scheduled Work" ("a repository call inside the per-item loop is a defect"). */
    @Query("SELECT s FROM UserSubscription s WHERE s.status = 'ACTIVE' AND s.endDate < :today")
    List<UserSubscription> findActiveExpiredAsOf(@Param("today") LocalDate today);
}
