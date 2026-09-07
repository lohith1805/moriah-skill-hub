package com.moriah.skillhub.common.security;

/** Projection of the active subscription's plan flags — never a full entity (common/ never
 * imports the subscription/ package's entities, which don't exist until feature 07 anyway). */
public record EntitlementFlags(
        boolean allowsBatch,
        boolean allowsSprints,
        boolean allowsPip,
        boolean mentorSupport,
        boolean allowsInternshipLetter,
        boolean allowsClientProject,
        Integer tierRank
) {

    /** No active subscription — every entitlement is denied, no tier. */
    public static final EntitlementFlags NONE = new EntitlementFlags(false, false, false, false, false, false, null);
}
