package com.moriah.skillhub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Every error code the system can return, with the HTTP status it maps to and a default
 * human-readable message. A string literal at a throw site is never acceptable — add a
 * constant here instead.
 * <p>
 * Feature-specific {@code *_NOT_FOUND} codes (e.g. {@code BATCH_NOT_FOUND}) are added here as
 * each feature that needs them is built, per the table in {@code context/code-standards.md}.
 */
public enum ErrorCode {

    // 400 — request malformed or fails validation
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "One or more fields failed validation."),
    OAUTH_PROFILE_INCOMPLETE(HttpStatus.BAD_REQUEST, "This OAuth provider did not supply enough profile information to complete login."),
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.BAD_REQUEST, "Webhook signature verification failed."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "This file type is not supported."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "This file exceeds the maximum upload size."),
    INVALID_PR_URL(HttpStatus.BAD_REQUEST, "This does not look like a valid GitHub pull request URL."),
    EXPORT_REPORT_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "This export report type is not supported."),
    EXPORT_FORMAT_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "This export format is not supported."),
    ROLE_NOT_STAFF_ASSIGNABLE(HttpStatus.BAD_REQUEST, "STUDENT and CLIENT are self-service roles and cannot be assigned to a staff invite."),

    // 401 — missing or expired credential
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email or password is incorrect."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "This refresh token is invalid or has been revoked."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "This refresh token has expired. Please log in again."),
    INVALID_2FA_CODE(HttpStatus.UNAUTHORIZED, "The 2FA code is incorrect or has expired."),
    INVALID_2FA_CHALLENGE(HttpStatus.UNAUTHORIZED, "This 2FA challenge is invalid or has expired. Please log in again."),

    // 403 — authenticated, but not permitted
    INSUFFICIENT_ROLE(HttpStatus.FORBIDDEN, "Your role does not permit this action."),
    ENTITLEMENT_REQUIRED(HttpStatus.FORBIDDEN, "Your current plan does not include this feature."),
    NOT_RESOURCE_OWNER(HttpStatus.FORBIDDEN, "You do not have access to this resource."),
    ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Please verify your email before logging in."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "This account is suspended."),
    ACCOUNT_INVITE_PENDING(HttpStatus.FORBIDDEN, "Finish setting your password from the invite link before signing in."),
    ACCOUNT_PENDING_APPROVAL(HttpStatus.FORBIDDEN, "Your registration is still awaiting review. We'll email you once it's approved."),
    ACCOUNT_REGISTRATION_REJECTED(HttpStatus.FORBIDDEN, "This registration request was not approved."),
    NOT_BATCH_OWNER(HttpStatus.FORBIDDEN, "You are not the PM for this batch."),
    NOT_BATCH_MEMBER(HttpStatus.FORBIDDEN, "This student is not an active member of this batch."),
    PR_AUTHOR_MISMATCH(HttpStatus.FORBIDDEN, "This pull request was not opened by your linked GitHub account."),
    NOT_REPORTING_MANAGER(HttpStatus.FORBIDDEN, "You are not this employee's reporting manager."),
    SELF_DECISION_NOT_ALLOWED(HttpStatus.FORBIDDEN, "You cannot decide on your own request."),

    // 405 — the path exists but not for this HTTP method
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This HTTP method is not supported for this endpoint."),

    // 404 — generic fallback; prefer a feature-specific *_NOT_FOUND code where one exists
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found."),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "This subscription plan does not exist or is no longer active."),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "No active subscription was found for this account."),
    SUBSCRIPTION_ALREADY_ACTIVE(HttpStatus.CONFLICT, "You already have an active subscription — manage it from your account instead of purchasing again."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "This payment could not be found."),
    INVOICE_NOT_FOUND(HttpStatus.NOT_FOUND, "No invoice PDF is available for this payment yet."),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "This coupon code does not exist."),
    RESUME_NOT_FOUND(HttpStatus.NOT_FOUND, "No resume has been uploaded for this account yet."),
    SIGNATURE_NOT_FOUND(HttpStatus.NOT_FOUND, "No signature has been saved for this account yet."),
    PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, "This portfolio does not exist."),
    BATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "This batch does not exist."),
    BATCH_STUDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "This student is not enrolled in this batch."),
    SPRINT_NOT_FOUND(HttpStatus.NOT_FOUND, "This sprint does not exist."),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "This task does not exist."),
    PR_NOT_FOUND(HttpStatus.NOT_FOUND, "This pull request could not be found on GitHub."),
    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "This submission does not exist."),
    STANDUP_NOT_FOUND(HttpStatus.NOT_FOUND, "This standup does not exist."),
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "This assessment does not exist."),
    QUIZ_ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "This attempt does not exist."),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "This project does not exist."),
    BUG_CHALLENGE_NOT_FOUND(HttpStatus.NOT_FOUND, "This bug-fix challenge does not exist."),
    CHALLENGE_SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "This challenge submission does not exist."),
    PIP_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "This PIP record does not exist."),
    PIP_MILESTONE_NOT_FOUND(HttpStatus.NOT_FOUND, "This PIP milestone does not exist."),
    PIP_RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "This PIP rule code does not exist."),
    LEAD_NOT_FOUND(HttpStatus.NOT_FOUND, "This lead does not exist."),
    LEAD_CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND, "This campaign does not exist."),
    INTERVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "This interview does not exist."),
    RECRUITMENT_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "This recruitment request does not exist."),
    PLACEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "This placement does not exist."),
    SALES_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "No sales target has been set for you this month."),
    EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND, "This employee record does not exist."),
    EMPLOYEE_EXIT_NOT_FOUND(HttpStatus.NOT_FOUND, "This exit record does not exist."),
    EMPLOYEE_ONBOARDING_NOT_FOUND(HttpStatus.NOT_FOUND, "This onboarding record does not exist."),
    DISCIPLINARY_ACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "This disciplinary action does not exist."),
    QUESTION_BANK_NOT_FOUND(HttpStatus.NOT_FOUND, "This question bank does not exist."),
    HR_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "This document does not exist."),
    LEAVE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "This leave request does not exist."),
    STAFF_ATTENDANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "No attendance record for this staff member on this date."),
    CERTIFICATE_NOT_FOUND(HttpStatus.NOT_FOUND, "This certificate does not exist."),
    CLIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "No client record is linked to your account."),
    CLIENT_PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "This client project does not exist."),
    REQUIREMENT_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "This requirement document does not exist."),

    // 409 — a business rule was violated
    BUSINESS_RULE_VIOLATION(HttpStatus.CONFLICT, "The request could not be completed."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "An account with this email already exists."),
    PHONE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "An account with this phone number already exists."),
    PLAN_CODE_TAKEN(HttpStatus.CONFLICT, "A subscription plan with this code already exists."),
    INVALID_OR_EXPIRED_VERIFICATION_TOKEN(HttpStatus.CONFLICT, "This verification link is invalid or has expired."),
    INVALID_OR_EXPIRED_RESET_TOKEN(HttpStatus.CONFLICT, "This password reset link is invalid or has expired."),
    INVALID_OR_EXPIRED_INVITE_TOKEN(HttpStatus.CONFLICT, "This invite link is invalid, has expired, or has already been used."),
    INVITE_NOT_PENDING(HttpStatus.CONFLICT, "This user's invite is not pending — it may already have been accepted."),
    CLIENT_REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "This client registration has already been decided."),
    TWO_FACTOR_ALREADY_ENABLED(HttpStatus.CONFLICT, "Two-factor authentication is already enabled on this account."),
    TWO_FACTOR_NOT_ENABLED(HttpStatus.CONFLICT, "Two-factor authentication is not enabled on this account."),
    COUPON_CODE_TAKEN(HttpStatus.CONFLICT, "A coupon with this code already exists."),
    COUPON_EXPIRED(HttpStatus.CONFLICT, "This coupon is not valid right now."),
    COUPON_EXHAUSTED(HttpStatus.CONFLICT, "This coupon has reached its redemption limit."),
    COUPON_ALREADY_REDEEMED(HttpStatus.CONFLICT, "You have already redeemed this coupon."),
    BATCH_FULL(HttpStatus.CONFLICT, "This batch is already at capacity."),
    BATCH_STUDENT_ALREADY_ENROLLED(HttpStatus.CONFLICT, "This student is already enrolled in this batch."),
    BATCH_CAPACITY_BELOW_ENROLLED(HttpStatus.CONFLICT, "Capacity cannot be set below the number of students already enrolled."),
    SPRINT_NUMBER_TAKEN(HttpStatus.CONFLICT, "A sprint with this number already exists in the batch."),
    SPRINT_DATE_OVERLAP(HttpStatus.CONFLICT, "This sprint's dates overlap another sprint in the same batch."),
    SPRINT_ALREADY_ACTIVE(HttpStatus.CONFLICT, "Another sprint in this batch is already active."),
    SPRINT_PREVIOUS_NOT_COMPLETED(HttpStatus.CONFLICT, "The previous sprint must be completed before this one can be activated."),
    SPRINT_INVALID_TRANSITION(HttpStatus.CONFLICT, "This sprint status transition is not allowed."),
    TASK_INVALID_TRANSITION(HttpStatus.CONFLICT, "This task status transition is not allowed."),
    TASK_PULL_BLOCKED_BY_PIP(HttpStatus.CONFLICT, "This student cannot pull new tasks while on an active PIP restriction."),
    ASSIGNMENT_WINDOW_WEEK_TAKEN(HttpStatus.CONFLICT, "An assignment window already exists for this batch and week."),
    ASSIGNMENT_WINDOW_TASK_WRONG_BATCH(HttpStatus.CONFLICT, "The linked task does not belong to this batch."),
    STANDUP_INVALID_TRANSITION(HttpStatus.CONFLICT, "This standup status transition is not allowed."),
    STANDUP_CANCELLED(HttpStatus.CONFLICT, "This standup was cancelled."),
    STANDUP_ALREADY_FINALISED(HttpStatus.CONFLICT, "This standup has already been finalised for attendance."),
    QUESTION_BANK_EMPTY(HttpStatus.CONFLICT, "This question bank has no questions to publish."),
    QUIZ_INACTIVE(HttpStatus.CONFLICT, "This assessment is not currently active."),
    QUIZ_MAX_ATTEMPTS_EXCEEDED(HttpStatus.CONFLICT, "You have already used every attempt allowed for this assessment."),
    QUIZ_ATTEMPT_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "This attempt has already been submitted or has expired."),
    PROJECT_NOT_PUBLISHED(HttpStatus.CONFLICT, "This project is not published and cannot be attached to a task."),
    PROJECT_INVALID_TRANSITION(HttpStatus.CONFLICT, "This project status transition is not allowed."),
    PIP_RECORD_NOT_OPEN(HttpStatus.CONFLICT, "This PIP record has already been reviewed."),
    PIP_ALREADY_OPEN(HttpStatus.CONFLICT, "This student already has an open PIP record."),
    PIP_MILESTONE_ALREADY_COMPLETED(HttpStatus.CONFLICT, "This milestone has already been completed."),
    PIP_MILESTONE_NOT_PENDING(HttpStatus.CONFLICT, "Only a pending milestone can be removed."),
    PIP_CLEARANCE_CRITERIA_NOT_MET(HttpStatus.CONFLICT, "This student does not yet meet the clearance criteria (task completion >= 85% and no unsatisfactory reviews)."),
    LEAD_PIPELINE_SKIP(HttpStatus.CONFLICT, "This status change would skip a pipeline stage."),
    LEAD_ALREADY_TERMINAL(HttpStatus.CONFLICT, "This lead has already reached a terminal status (ENROLLED or LOST)."),
    LEAD_STATUS_UNCHANGED(HttpStatus.CONFLICT, "This lead is already in that status."),
    LEAD_BACKWARD_REASON_REQUIRED(HttpStatus.CONFLICT, "A reason is required to move a lead backward in the pipeline."),
    LEAD_ALREADY_ARCHIVED(HttpStatus.CONFLICT, "This lead has already been archived."),
    EMPLOYEE_ALREADY_EXISTS(HttpStatus.CONFLICT, "This user already has an employee record."),
    HR_DOCUMENT_ALREADY_DECIDED(HttpStatus.CONFLICT, "This document has already been verified or rejected."),
    LEAVE_REQUEST_ALREADY_DECIDED(HttpStatus.CONFLICT, "This leave request has already been decided."),
    LEAVE_OVERLAPS_APPROVED_LEAVE(HttpStatus.CONFLICT, "This overlaps another already-approved leave for this employee."),
    PAYROLL_ALREADY_GENERATED(HttpStatus.CONFLICT, "Payroll for this employee and month has already been generated."),
    LETTER_NOT_ELIGIBLE(HttpStatus.CONFLICT, "This person is not eligible for this letter (must be graduated or cleanly exited, never terminated)."),
    CERTIFICATE_ALREADY_REVOKED(HttpStatus.CONFLICT, "This certificate has already been revoked."),
    PAYMENT_NOT_REFUNDABLE(HttpStatus.CONFLICT, "Only a captured payment can be refunded, and only once."),

    // 429
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again shortly."),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred."),
    NOTIFICATION_CHANNEL_NOT_SUPPORTED(HttpStatus.INTERNAL_SERVER_ERROR, "This notification channel has no dispatcher configured yet."),
    EXPORT_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "This export could not be generated. Please try again."),

    // 502 — an upstream service (not the caller, not us) failed
    PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "The payment gateway could not be reached. Please try again."),
    STORAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "The file could not be stored. Please try again.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
