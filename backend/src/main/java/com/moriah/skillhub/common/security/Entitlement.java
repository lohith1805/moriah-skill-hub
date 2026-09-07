package com.moriah.skillhub.common.security;

import java.util.function.Predicate;

/** build-plan.md feature 04's exact enum. */
public enum Entitlement {
    BATCH(EntitlementFlags::allowsBatch),
    SPRINTS(EntitlementFlags::allowsSprints),
    PIP(EntitlementFlags::allowsPip),
    MENTOR(EntitlementFlags::mentorSupport),
    INTERNSHIP_LETTER(EntitlementFlags::allowsInternshipLetter),
    CLIENT_PROJECT(EntitlementFlags::allowsClientProject);

    private final Predicate<EntitlementFlags> test;

    Entitlement(Predicate<EntitlementFlags> test) {
        this.test = test;
    }

    boolean isGrantedBy(EntitlementFlags flags) {
        return test.test(flags);
    }
}
