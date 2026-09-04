package com.moriah.skillhub.subscription.repository;

import com.moriah.skillhub.subscription.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByCode(String code);

    /** Admin plan create (gap B1.13) rejects a duplicate {@code code} with a clean 409 before
     * the unique constraint would surface as a raw {@code DataIntegrityViolation}. */
    boolean existsByCode(String code);

    List<SubscriptionPlan> findByActiveTrueOrderByTierRankAsc();

    /** Admin plan listing (gap B1.13) — every plan, active or not, tier order. */
    List<SubscriptionPlan> findAllByOrderByTierRankAsc();
}
