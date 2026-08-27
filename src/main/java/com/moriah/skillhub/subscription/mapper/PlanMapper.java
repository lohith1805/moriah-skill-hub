package com.moriah.skillhub.subscription.mapper;

import com.moriah.skillhub.subscription.dto.PlanResponse;
import com.moriah.skillhub.subscription.entity.SubscriptionPlan;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PlanMapper {

    PlanResponse toResponse(SubscriptionPlan plan);
}
