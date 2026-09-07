package com.moriah.skillhub.talent.entity;

/** Mirrors {@code chk_recruitment_requests_status} in {@code V27__recruitment_requests.sql}.
 * {@code PENDING} is the only state a decision may be made from. */
public enum RecruitmentRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
