package com.moriah.skillhub.subscription;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.subscription.dto.CreatePlanRequest;
import com.moriah.skillhub.subscription.dto.PlanResponse;
import com.moriah.skillhub.subscription.dto.UpdatePlanRequest;
import com.moriah.skillhub.subscription.entity.SubscriptionPlan;
import com.moriah.skillhub.subscription.mapper.PlanMapper;
import com.moriah.skillhub.subscription.mapper.SubscriptionMapper;
import com.moriah.skillhub.subscription.repository.SubscriptionPlanRepository;
import com.moriah.skillhub.subscription.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** feature 22: {@code PUT /admin/plans/{id}}'s entity-mutation logic in isolation — the {@code
 * @CacheEvict} annotation itself is proxy-based AOP a plain Mockito unit test can't exercise
 * (same reasoning {@code EntitlementFlagsLoader}'s Javadoc gives for its own {@code @Cacheable}
 * split); the real eviction is proven in {@code AdminMetricsFlowIT} by updating a plan then
 * immediately re-reading {@code GET /plans} through a real Redis-backed cache manager and seeing
 * the change without waiting out the 10-minute default TTL. */
@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private PlanMapper planMapper;
    @Mock
    private SubscriptionMapper subscriptionMapper;

    @InjectMocks
    private EntitlementService entitlementService;

    private SubscriptionPlan plan;

    @BeforeEach
    void setUp() {
        plan = new SubscriptionPlan();
        plan.setId(1L);
        plan.setCode("PRO");
        plan.setName("Pro");
        plan.setPriceInr(new BigDecimal("9999.00"));
        plan.setTierRank(2);
        plan.setDurationDays(180);
        plan.setActive(true);
    }

    @Test
    void updatePlan_mutatesFieldsAndSaves() {
        when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(planMapper.toResponse(plan)).thenReturn(new PlanResponse(
                "PRO", "Pro Updated", new BigDecimal("14999.00"), 2, 365,
                true, true, true, false, false, false));

        UpdatePlanRequest request = new UpdatePlanRequest(
                "Pro Updated", new BigDecimal("14999.00"), 365, 10,
                true, true, true, false, false, false, false);

        PlanResponse response = entitlementService.updatePlan(1L, request);

        assertThat(plan.getName()).isEqualTo("Pro Updated");
        assertThat(plan.getPriceInr()).isEqualByComparingTo("14999.00");
        assertThat(plan.getDurationDays()).isEqualTo(365);
        assertThat(plan.getMaxProjects()).isEqualTo(10);
        assertThat(plan.isActive()).isFalse();
        assertThat(response.name()).isEqualTo("Pro Updated");
        verify(subscriptionPlanRepository).save(plan);
    }

    @Test
    void updatePlan_unknownId_throwsNotFound() {
        when(subscriptionPlanRepository.findById(99L)).thenReturn(Optional.empty());

        UpdatePlanRequest request = new UpdatePlanRequest(
                "X", BigDecimal.TEN, 30, null, false, false, false, false, false, false, true);

        assertThatThrownBy(() -> entitlementService.updatePlan(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_NOT_FOUND);
    }

    private CreatePlanRequest createRequest(String code) {
        return new CreatePlanRequest(code, "Weekend Sprint", new BigDecimal("4999.00"), 6, 90, 3,
                true, true, true, false, false, false, true);
    }

    @Test
    void createPlan_persistsEveryFieldIncludingCodeAndTierRank() {
        when(subscriptionPlanRepository.existsByCode("WEEKEND")).thenReturn(false);
        when(planMapper.toResponse(org.mockito.ArgumentMatchers.any(SubscriptionPlan.class)))
                .thenReturn(new PlanResponse("WEEKEND", "Weekend Sprint", new BigDecimal("4999.00"),
                        6, 90, true, true, true, false, false, false));

        entitlementService.createPlan(createRequest("WEEKEND"));

        org.mockito.ArgumentCaptor<SubscriptionPlan> captor =
                org.mockito.ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("WEEKEND");
        assertThat(captor.getValue().getTierRank()).isEqualTo(6);
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void createPlan_duplicateCode_throwsConflict() {
        when(subscriptionPlanRepository.existsByCode("PRO")).thenReturn(true);

        assertThatThrownBy(() -> entitlementService.createPlan(createRequest("PRO")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_CODE_TAKEN);

        verify(subscriptionPlanRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deactivatePlan_setsInactiveAndSaves() {
        when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.of(plan));

        entitlementService.deactivatePlan(1L);

        assertThat(plan.isActive()).isFalse();
        verify(subscriptionPlanRepository).save(plan);
    }

    @Test
    void deactivatePlan_unknownId_throwsNotFound() {
        when(subscriptionPlanRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> entitlementService.deactivatePlan(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_NOT_FOUND);
    }
}
