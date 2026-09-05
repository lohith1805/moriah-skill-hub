package com.moriah.skillhub.client.entity;

/** architecture.md: "BRD/SRS/FRS/user story." {@link #OTHER} is a later addition for documents
 * that don't fit any of those four — e.g. a test plan or a change-request note — so a BA isn't
 * forced to mislabel something just to get it into the system. {@code
 * RequirementDocumentApprovalService#REQUIRED_ROLES} falls back to its
 * BUSINESS_ANALYST+DEVELOPER default for it, same as SRS/USER_STORY. */
public enum RequirementDocumentType {
    BRD,
    SRS,
    FRS,
    USER_STORY,
    OTHER
}
