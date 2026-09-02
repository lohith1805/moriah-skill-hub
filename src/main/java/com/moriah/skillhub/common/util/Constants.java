package com.moriah.skillhub.common.util;

/**
 * Every threshold, limit, and magic value that isn't already externalized via a
 * {@code @ConfigurationProperties} record or a config table lives here (code-standards.md
 * "Constants"). Values that come from {@code application.yml} (JWT expiry, rate limits, cache
 * TTLs) belong in their own properties record instead — this class is for values with no
 * per-environment reason to change.
 */
public final class Constants {

    private Constants() {
    }

    /** How long a freshly issued email-verification link stays valid (build-plan.md feature 03). */
    public static final int EMAIL_VERIFICATION_TOKEN_TTL_HOURS = 24;

    /** How long a freshly issued password-reset link stays valid (build-plan.md feature 03). */
    public static final int PASSWORD_RESET_TOKEN_TTL_HOURS = 1;

    /** How long a staff-invite link stays valid — a full working week, so an invite sent on a
     * Friday is still usable the following week (frontend-integration decision, 2026-09-02). */
    public static final int STAFF_INVITE_TOKEN_TTL_HOURS = 168;

    /** Invoice numbers are {@code MSH-INV-<payment id, zero-padded to 6 digits>} — collision-free
     * by construction (payments.id is already unique and monotonic), reconciliation-friendly
     * (build-plan.md feature 07; code-standards.md's own Constants example lists this prefix). */
    public static final String INVOICE_PREFIX = "MSH-INV";

    /** Certificate numbers are {@code MSH-CERT-<issue year>-<certificate id, zero-padded to 6
     * digits>} (build-plan.md feature 20) — the same collision-free-by-construction reasoning as
     * {@link #INVOICE_PREFIX} (a row's own auto-increment {@code id} is already unique and
     * monotonic), with a year segment added since a certificate, unlike an invoice, is a
     * human-facing, printed document where "issued in 2027" is meaningful at a glance.
     * {@code CertificateService#issue} saves the row once (unset {@code certificateNumber},
     * assigning the id), then sets and saves this value — the id doesn't exist until after the
     * first insert, so there's no way to compute it beforehand. */
    public static final String CERTIFICATE_PREFIX = "MSH-CERT";

    /** Upload cap for anything going through {@code StorageService} — resumes, project assets,
     * HR documents, submissions (code-standards.md "File uploads", build-plan.md feature 08). */
    public static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    /** Presigned GET URL lifetime (architecture.md "Object Storage": "All access via presigned
     * URLs with 15-minute TTL"). */
    public static final long PRESIGNED_URL_TTL_MINUTES = 15;

    /** How many times {@code NotificationWorker} retries a failed dispatch before marking the row
     * {@code FAILED} (build-plan.md / library-docs.md "Notification Dispatch": "retries up to 3
     * times with exponential backoff"). */
    public static final int NOTIFICATION_MAX_ATTEMPTS = 3;

    /** How long an entry may sit in {@code queue:notifications:processing} before the reaper
     * considers it abandoned by a dead worker and requeues it (library-docs.md "Reliable Queue"). */
    public static final int NOTIFICATION_PROCESSING_STALE_MINUTES = 5;

    /** How old a {@code QUEUED} {@code notifications} row must be before {@code
     * NotificationReaperJob} treats it as possibly having lost its Redis queue entry entirely
     * (not just gone stale in the processing list — library-docs.md "Redis": "losing Redis must
     * degrade performance, never lose data") and re-pushes it. Deliberately longer than {@link
     * #NOTIFICATION_PROCESSING_STALE_MINUTES} so a row that's still legitimately in flight through
     * its first, normal delivery attempt is never mistaken for orphaned. */
    public static final int NOTIFICATION_ORPHAN_RECONCILE_MINUTES = 15;

    /** How many of {@code UserProfile}'s fields (build-plan.md feature 09: "completion_percent
     * computed server-side") {@code ProfileService.recalculateCompletion} checks — bio, location,
     * currentTitle, experienceLevel, yearsExperience, skills, education, workExperience,
     * resumeKey, githubUsername. Changing which fields count means changing this too. */
    public static final int PROFILE_COMPLETION_FIELD_COUNT = 10;

    /** Random bytes (hex-encoded to 8 characters) appended to a slugified full name to make
     * {@code portfolio_slug} unique (build-plan.md feature 09: "portfolio_slug unique, name plus
     * random suffix"). */
    public static final int PORTFOLIO_SLUG_SUFFIX_BYTES = 4;

    /** build-plan.md feature 11: "Sprints run 1-2 weeks." Enforced in {@code
     * CreateSprintRequest}/{@code UpdateSprintRequest}'s compact constructors — inclusive bounds,
     * a 7-day sprint and a 14-day sprint are both legal. */
    public static final int SPRINT_MIN_DURATION_DAYS = 7;
    public static final int SPRINT_MAX_DURATION_DAYS = 14;

    /** {@code standups.late_cutoff_minutes}' default when a PM doesn't supply one (build-plan.md
     * feature 13; matches V7's own {@code DEFAULT 15} at the schema level). {@code
     * AttendanceFinalisationJob} always reads the per-standup column, never this constant
     * directly — this only seeds the column's initial value. */
    public static final int ATTENDANCE_DEFAULT_LATE_CUTOFF_MINUTES = 15;

    /** {@code quizzes.pass_percentage}'s default when a PM doesn't supply one (build-plan.md
     * feature 14: "Pass baseline from quizzes.pass_percentage, default
     * Constants.QUIZ_PASS_PERCENTAGE" — named explicitly in the spec). Matches V8's own
     * {@code DEFAULT 60} at the schema level. */
    public static final int QUIZ_PASS_PERCENTAGE = 60;

    /** {@code quizzes.max_attempts}' default when a PM doesn't supply one — matches V8's own
     * {@code DEFAULT 1}. */
    public static final int QUIZ_DEFAULT_MAX_ATTEMPTS = 1;

    /** How many numeric-suffix retries {@code ProjectService#saveWithUniqueSlug} attempts on a
     * {@code uq_projects_slug} collision before giving up (build-plan.md feature 15). Two
     * concurrent creates with the same title is the only realistic way to exhaust more than one
     * or two attempts; this is a generous ceiling against a pathological retry loop, not a value
     * expected to matter in practice. */
    public static final int MAX_SLUG_GENERATION_ATTEMPTS = 5;

    /** How many retries {@code CertificateService#generateUniqueVerificationCode} attempts on a
     * {@code uq_certificates_verification_code} collision before giving up — same reasoning and
     * same ceiling as {@link #MAX_SLUG_GENERATION_ATTEMPTS}: a 12-character code drawn from a
     * ~32-character alphabet via {@code SecureRandom} has an astronomically small collision
     * chance per attempt, so this is a generous ceiling against a pathological retry loop, not a
     * value expected to matter in practice. Checked via a repository {@code
     * existsByVerificationCode} call and retried, not an insert-and-catch race like {@code
     * ProjectService#saveWithUniqueSlug} — certificate issuance is a low-frequency admin action
     * with no concurrent-create scenario worth optimizing for (build-plan.md feature 20). */
    public static final int MAX_CODE_GENERATION_ATTEMPTS = 5;

    /** {@code StudentMetricsService#applyAttendance}'s rolling window (architecture.md
     * `student_metrics.attendance_present`/`attendance_total`: "Rolling 14-day window") and the
     * chunk size for its batched {@code student_metrics} upsert (build-plan.md feature 16:
     * "upserted in batches of 500"). */
    public static final int STUDENT_METRICS_ATTENDANCE_WINDOW_DAYS = 14;
    public static final int STUDENT_METRICS_UPSERT_BATCH_SIZE = 500;

    /** The window baked into {@code student_metrics.tasks_overdue_48h}'s own name and definition
     * (architecture.md V9: "Committed stories &gt; 48h past `due_at`") — schema-fixed, the same way
     * {@link #STUDENT_METRICS_ATTENDANCE_WINDOW_DAYS} is, and deliberately NOT sourced from {@code
     * pip_rules} (that table doesn't exist until feature 17's V11). This is distinct from the PIP
     * rule's own decision threshold ("≥ 1 such task") that feature 17's `PROJECT_DELAY` evaluator
     * reads from {@code pip_rules} at evaluation time — code-standards.md's "PIP thresholds live in
     * `pip_rules`, not Java" rule governs that count, not the window used to populate this column.
     * See progress-tracker.md's feature 16 decision log for the full reconciliation. */
    public static final int STUDENT_METRICS_TASK_OVERDUE_HOURS = 48;

    /** How long a triggered {@code pip_records} row's remediation window runs (build-plan.md
     * feature 17: "start today, end +15 days"). Not one of the six {@code pip_rules} thresholds —
     * {@code pip_rules.rule_code} is a fixed six-value CHECK constraint (V11) with no seventh
     * "duration" or "clearance" row, so this and the two constants below are plain Java constants,
     * not config-table values, the same distinction feature 16's decision log draws for {@code
     * STUDENT_METRICS_TASK_OVERDUE_HOURS}. */
    public static final int PIP_RECORD_DURATION_DAYS = 15;

    /** Due date offset (from {@code pip_records.start_date}) for the one auto-generated {@code
     * pip_milestones} row a trigger creates — 5 days before the day-15 review, giving the student a
     * checkpoint partway through, not just a single pass/fail gate at the very end. */
    public static final int PIP_MILESTONE_DUE_DAYS = 10;

    /** build-plan.md feature 22: "delivered by presigned URL above 1,000 rows." Both branches of
     * {@code ExportService} currently deliver via presigned URL (a documented simplification —
     * see its own Javadoc, no existing precedent in this codebase for returning raw file bytes in
     * a JSON response), but the row count is still compared against this threshold to set {@code
     * ExportResponse.deliveredInline} — the observable seam a future "return bytes for small
     * exports" change would hang off, and what {@code ExportServiceTest} exercises directly. */
    public static final int EXPORT_SMALL_ROW_THRESHOLD = 1000;

    // The clearance-gate task-completion percentage lives in PipClearanceProperties
    // (moriah.pip.clearance.min-task-completion-percent), not here — a `/review` finding: unlike
    // the two constants above, this value is one AGENTS.md explicitly calls a PIP threshold
    // ("PIP thresholds are rows in pip_rules... hardcoding them in Java is a defect"), so it needed
    // to be genuinely externalized, not just documented as deliberately Java-side.
}
