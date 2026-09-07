package com.moriah.skillhub.subscription.mapper;

import com.moriah.skillhub.subscription.dto.AdminPlanResponse;
import com.moriah.skillhub.subscription.dto.PlanResponse;
import com.moriah.skillhub.subscription.entity.SubscriptionPlan;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PlanMapper {

    PlanResponse toResponse(SubscriptionPlan plan);

    /** Admin listing — adds {@code id}, {@code maxProjects} and {@code active} on top of {@link
     * #toResponse}. {@code id} comes from {@code BaseEntity.getId()}. */
    AdminPlanResponse toAdminResponse(SubscriptionPlan plan);
}
