package com.moriah.skillhub.subscription;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.subscription.dto.PlanResponse;
import com.moriah.skillhub.subscription.dto.SubscriptionResponse;
import com.moriah.skillhub.subscription.entity.SubscriptionPlan;
import com.moriah.skillhub.subscription.entity.SubscriptionStatus;
import com.moriah.skillhub.subscription.mapper.PlanMapper;
import com.moriah.skillhub.subscription.mapper.SubscriptionMapper;
import com.moriah.skillhub.subscription.repository.SubscriptionPlanRepository;
import com.moriah.skillhub.subscription.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Backs both {@code PlanController} and {@code SubscriptionController} — not named as a separate
 * "PlanService" in architecture.md's package diagram, which lists only {@code EntitlementService}
 * for this package's read-side concerns.
 */
@Service
@RequiredArgsConstructor
public class EntitlementService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final PlanMapper planMapper;
    private final SubscriptionMapper subscriptionMapper;

    /** build-plan.md feature 07: "GET /plans public, cached in Redis." Uses {@code RedisConfig}'s
     * default TTL (10 minutes) — rarely-changing reference data, no per-cache override needed. */
    @Cacheable("plans")
    @Transactional(readOnly = true)
    public List<PlanResponse> listActivePlans() {
        return subscriptionPlanRepository.findByActiveTrueOrderByTierRankAsc().stream()
                .map(planMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getCurrentSubscription(Long userId) {
        return userSubscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .map(subscriptionMapper::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    }

    /** `/architect feature 10`: {@code BatchService} needs to translate a batch's minimum-tier
     * plan code into {@code subscription_plans.id} at creation/update time — this is the clean
     * cross-package "service interface, never the repository or entity directly" call for that
     * (architecture.md), so {@code batch/} never touches {@code SubscriptionPlanRepository}. */
    @Transactional(readOnly = true)
    public Long resolvePlanId(String planCode) {
        return subscriptionPlanRepository.findByCode(planCode)
                .filter(SubscriptionPlan::isActive)
                .map(SubscriptionPlan::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
    }

    /** The read-side counterpart of {@link #resolvePlanId} — {@code BatchService} uses this to
     * render {@code batches.plan_tier_min_id} back into a plan code for {@code BatchResponse}. */
    @Transactional(readOnly = true)
    public Optional<String> findPlanCode(Long planId) {
        if (planId == null) {
            return Optional.empty();
        }
        return subscriptionPlanRepository.findById(planId).map(SubscriptionPlan::getCode);
    }

    /** {@code BatchService.list} calls this once per page, not once per row — same N+1
     * prevention reasoning as {@link #listActivePlans}, reusing the same rarely-changing-
     * reference-data cache-everything approach (library-docs.md "Redis"). */
    @Cacheable("planCodesById")
    @Transactional(readOnly = true)
    public Map<Long, String> planCodesById() {
        return subscriptionPlanRepository.findAll().stream()
                .collect(Collectors.toMap(SubscriptionPlan::getId, SubscriptionPlan::getCode));
    }
}
