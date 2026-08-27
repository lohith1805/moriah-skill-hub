package com.moriah.skillhub.subscription.mapper;

import com.moriah.skillhub.subscription.dto.SubscriptionResponse;
import com.moriah.skillhub.subscription.entity.UserSubscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SubscriptionMapper {

    @Mapping(target = "planCode", source = "plan.code")
    @Mapping(target = "planName", source = "plan.name")
    SubscriptionResponse toResponse(UserSubscription subscription);
}
