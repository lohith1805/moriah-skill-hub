package com.moriah.skillhub.common.security;

import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * build-plan.md feature 04. Never reads entitlement from a JWT claim — {@code tier} is
 * deliberately not a claim (architecture.md "Authentication") so an upgrade takes effect without
 * re-login; the 60s cache in {@link EntitlementFlagsLoader} is the only staleness window.
 * <p>
 * No active subscription is a {@code 403 ENTITLEMENT_REQUIRED}, never a {@code 401} — the caller
 * is authenticated, just not entitled (build-plan.md feature 04 verify line).
 */
@Component
@RequiredArgsConstructor
public class EntitlementGuard {

    private final EntitlementFlagsLoader entitlementFlagsLoader;

    public void require(Long userId, Entitlement entitlement) {
        EntitlementFlags flags = entitlementFlagsLoader.load(userId);
        if (!entitlement.isGrantedBy(flags)) {
            throw new ForbiddenOperationException(ErrorCode.ENTITLEMENT_REQUIRED);
        }
    }
}
