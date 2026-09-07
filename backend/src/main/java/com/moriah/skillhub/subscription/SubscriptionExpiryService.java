package com.moriah.skillhub.subscription;

import com.moriah.skillhub.subscription.entity.SubscriptionStatus;
import com.moriah.skillhub.subscription.entity.UserSubscription;
import com.moriah.skillhub.subscription.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * A separate bean from {@link SubscriptionExpiryJob}, deliberately — code-standards.md
 * "Transactions": "Never rely on {@code @Transactional} on a private or self-invoked method — it
 * does nothing." {@code SubscriptionExpiryJob.expireOverdueSubscriptions()} is itself a {@code
 * @Scheduled} method invoked by Spring's scheduler directly on the target bean, not through the
 * transactional proxy; a same-class call from there to {@link #runExpiry} would be exactly that
 * self-invocation, silently turning the "one atomic sweep" this method's own Javadoc promises
 * into one auto-committing mini-transaction per {@code save} instead. Calling {@link #runExpiry}
 * on this separate bean instead goes through Spring's proxy like any other cross-bean call, so
 * {@code @Transactional} actually applies. Same fix, same reasoning as {@code
 * RefreshTokenRevocationService} (feature 03/04's own version of this bug) — see
 * progress-tracker.md.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionExpiryService {

    private final UserSubscriptionRepository userSubscriptionRepository;

    /** Audit 2026-08-31 (M7): "today" must be resolved in the jobs' scheduling zone, not the JVM
     * default — on a UTC host {@code LocalDate.now()} at 03:00 IST is still the previous calendar
     * day, expiring subscriptions a day early. */
    @Value("${moriah.jobs.zone}")
    private String jobsZone;

    /** One flat query for the whole cohort, processed in memory — code-standards.md "Async and
     * Scheduled Work": "a repository call inside the per-item loop is a defect." */
    @Transactional
    public int runExpiry() {
        List<UserSubscription> overdue = userSubscriptionRepository.findActiveExpiredAsOf(
                LocalDate.now(ZoneId.of(jobsZone)));
        for (UserSubscription subscription : overdue) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
        }
        userSubscriptionRepository.saveAll(overdue);
        return overdue.size();
    }
}
