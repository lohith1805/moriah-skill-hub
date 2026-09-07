# Code Standards

Implementation rules and conventions for the entire backend. The AI agent must follow these in every session without exception. These rules prevent pattern drift across sessions.

---

## Engineering Mindset

The AI agent on this project operates as a senior backend engineer. This means:

- **Think before implementing** — understand what is being built and why before writing a single line
- **Read context files first** — never assume, always verify against architecture.md and project-overview.md
- **Scope is sacred** — only build what the current feature requires. Never go beyond scope even if it seems helpful
- **Every feature must be testable** — if it cannot be verified with an HTTP call or an integration test immediately after implementation, it is incomplete
- **Clean over clever** — simple readable code that a junior developer can understand is always preferred over clever abstractions
- **One thing at a time** — complete one feature fully before touching the next
- **The database is the contract** — schema changes go through Flyway, never through entity annotations alone
- **Failures are expected** — external calls fail, gateways retry, students submit bad URLs. Handle it, log it, never let one failure corrupt a transaction

---

## Java 21

- Use `record` for all DTOs, request bodies, response bodies, and value objects. Never a class with getters for data transfer.
- Use `sealed interface` + records for closed result hierarchies where it clarifies intent
- Use pattern matching for `instanceof` and `switch` — no cast-after-check chains
- Use text blocks (`"""`) for multi-line SQL, JSON templates, and email bodies
- Use `var` only where the right-hand side makes the type obvious — never for method return values whose type is not evident at the call site
- Virtual threads are enabled. Never create a manual `Thread` or a fixed thread pool for I/O-bound work
- Never use `Optional` as a method parameter or a field. Return type only.
- Never return `null` from a service method — return `Optional`, an empty collection, or throw
- `final` on all fields that are not reassigned. Constructor injection makes this the default.
- No `java.util.Date`, no `Calendar`. `Instant` for timestamps, `LocalDate` for dates, `LocalDateTime` only where a zone is genuinely irrelevant.

---

## Spring Boot 3.5 Conventions

- **Constructor injection only.** No `@Autowired` on fields, ever. Use a single constructor so Spring injects it implicitly — no annotation needed.
- Prefer `@RequiredArgsConstructor` (Lombok) with `private final` fields
- `@Service` for business logic, `@Repository` only on custom repository implementations (Spring Data interfaces need no annotation), `@RestController` for HTTP
- `@Transactional` goes on service methods, never on controllers or repositories
- `@Transactional(readOnly = true)` on every read-only service method — it matters for Hibernate flush behaviour
- Use `RestClient` for outbound HTTP. Never `RestTemplate` (maintenance mode), never raw `HttpClient` unless streaming
- Configuration binds through `@ConfigurationProperties` records — never scatter `@Value` across classes
- Never use `@Component` on something that is clearly a service, repository, or controller
- Always read the Spring Boot 3.5 documentation before implementing a Spring-specific feature — APIs differ from older training data, particularly around Spring Security 6 and Spring Data 3

---

## Naming

| Element              | Convention                    | Example                              |
| -------------------- | ----------------------------- | ------------------------------------ |
| Package              | lowercase, singular           | `com.moriah.skillhub.sprint`         |
| Class                | PascalCase                    | `SprintService`                       |
| Interface            | PascalCase, no `I` prefix     | `StorageService`                      |
| Implementation       | `{Interface}Impl` or descriptive | `S3StorageService`                 |
| Entity               | Singular noun                 | `Sprint`, `TaskSubmission`            |
| Repository           | `{Entity}Repository`          | `SprintRepository`                    |
| Request DTO          | `{Action}{Entity}Request`     | `CreateSprintRequest`                 |
| Response DTO         | `{Entity}Response`            | `SprintResponse`                      |
| Mapper               | `{Entity}Mapper`              | `SprintMapper`                        |
| Exception            | `{Reason}Exception`           | `ResourceNotFoundException`           |
| Test                 | `{Class}Test` / `{Class}IT`   | `SprintServiceTest`, `SprintControllerIT` |
| DB table             | snake_case, plural            | `task_submissions`                    |
| DB column            | snake_case                    | `latest_commit_sha`                   |
| Migration            | `V{n}__{snake_description}.sql` | `V3__batches_sprints_tasks.sql`     |
| Constant             | UPPER_SNAKE_CASE              | `PIP_REMEDIATION_DAYS`                |
| Enum constant        | UPPER_SNAKE_CASE              | `CHANGES_REQUESTED`                   |

One public type per file. No nested public classes.

---

## Layer Rules

### Controller

```java
@RestController
@RequestMapping("/api/v1/sprints")
@RequiredArgsConstructor
@Tag(name = "Sprints")
public class SprintController {

    private final SprintService sprintService;

    @PostMapping
    @PreAuthorize("hasRole('TRAINER_PM')")
    public ResponseEntity<ApiResponse<SprintResponse>> create(
            @Valid @RequestBody CreateSprintRequest request,
            @CurrentUser Long userId) {

        SprintResponse response = sprintService.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<SprintResponse>>> list(
            @RequestParam Long batchId,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                sprintService.listByBatch(batchId, pageable)));
    }
}
```

- Every method has `@PreAuthorize` unless the endpoint is explicitly public
- Every request body has `@Valid`
- Every list endpoint takes `Pageable` with `@PageableDefault`
- No try/catch — `GlobalExceptionHandler` owns error mapping
- No business logic, no repository, no entity
- Return `ResponseEntity<ApiResponse<T>>` always

### Service

```java
@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintRepository sprintRepository;
    private final BatchRepository batchRepository;
    private final SprintMapper sprintMapper;
    private final AuditLogService auditLogService;

    @Transactional
    public SprintResponse create(CreateSprintRequest request, Long userId) {
        Batch batch = batchRepository.findById(request.batchId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BATCH_NOT_FOUND, request.batchId()));

        if (!batch.getPmId().equals(userId)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_BATCH_OWNER);
        }
        if (sprintRepository.existsByBatchIdAndSprintNumber(batch.getId(), request.sprintNumber())) {
            throw new BusinessException(ErrorCode.SPRINT_NUMBER_TAKEN);
        }

        Sprint sprint = sprintMapper.toEntity(request);
        sprint.setBatch(batch);
        sprint.setStatus(SprintStatus.PLANNED);
        sprintRepository.save(sprint);

        auditLogService.record(userId, "SPRINT_CREATED", "Sprint", sprint.getId(), null, sprint);
        return sprintMapper.toResponse(sprint);
    }
}
```

- Validate, authorize ownership, then mutate — in that order
- Throw domain exceptions, never return error strings
- Never return an entity — always map to a response record
- Audit every mutation that affects money, grades, roles, or PIP status
- Keep transactions short — never make an external HTTP call inside `@Transactional`

### Repository

```java
public interface SprintRepository extends JpaRepository<Sprint, Long> {

    boolean existsByBatchIdAndSprintNumber(Long batchId, Integer sprintNumber);

    Page<Sprint> findByBatchIdOrderBySprintNumberAsc(Long batchId, Pageable pageable);

    @Query("""
        SELECT new com.moriah.skillhub.sprint.dto.VelocityProjection(
            s.id, s.sprintNumber, s.plannedPoints, s.completedPoints)
        FROM Sprint s
        WHERE s.batch.id = :batchId AND s.status = 'COMPLETED'
        """)
    List<VelocityProjection> findVelocity(@Param("batchId") Long batchId);
}
```

- Derived query methods for simple cases; `@Query` with a text block when the derived name would exceed roughly six words
- Always project into a record for read-only aggregates — never fetch full entities to compute a number
- Native SQL only for the remaining admin views, metrics upserts, and bulk operations, and always with a comment explaining why JPQL was insufficient
- Never `findAll()` without a `Pageable`

### DTO

```java
public record CreateSprintRequest(
        @NotNull Long batchId,
        @NotNull @Min(1) @Max(52) Integer sprintNumber,
        @NotBlank @Size(max = 500) String goal,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull LocalDate endDate,
        @Min(0) @Max(200) Integer plannedPoints
) {
    public CreateSprintRequest {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("endDate must be after startDate");
        }
    }
}
```

- Records only. Validation annotations on components. Cross-field checks in the compact constructor.
- Request and response records are separate types — never reuse one for both
- Never expose `users.id`, `password_hash`, `two_factor_secret`, or any token in a response record

### Entity

```java
@Entity
@Table(name = "sprints", uniqueConstraints =
    @UniqueConstraint(columnNames = {"batch_id", "sprint_number"}))
@Getter @Setter
@NoArgsConstructor
public class Sprint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(name = "sprint_number", nullable = false)
    private Integer sprintNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SprintStatus status;
}
```

- **Every** `@ManyToOne` and `@OneToOne` is `FetchType.LAZY`. No exceptions — the default `EAGER` is a defect.
- `@Enumerated(EnumType.STRING)` always. Never `ORDINAL`.
- No `@OneToMany` unless the collection is genuinely needed for cascade behaviour — query from the owning side instead
- No `cascade = CascadeType.ALL` on `@ManyToOne`
- No validation annotations on entities — validation belongs on request records
- No business methods on entities beyond trivial derived getters

---

## Response Envelope

Every endpoint returns this shape. No exceptions.

```java
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDetail error,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> failure(ErrorDetail error) {
        return new ApiResponse<>(false, null, error, Instant.now());
    }
}
```

```json
{
  "success": true,
  "data": { "id": 12, "sprintNumber": 3 },
  "error": null,
  "timestamp": "2026-08-21T09:14:22.113Z"
}
```

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "SPRINT_NUMBER_TAKEN",
    "message": "A sprint with this number already exists in the batch.",
    "fieldErrors": []
  },
  "timestamp": "2026-08-21T09:14:22.113Z"
}
```

The frontend depends on this contract. Changing it breaks three engineers at once.

---

## Error Handling

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("[{}] {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(ErrorDetail.of(ex.getErrorCode())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorDetail.of(ErrorCode.VALIDATION_FAILED, fields)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("[UNEXPECTED] {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(ErrorDetail.of(ErrorCode.INTERNAL_ERROR)));
    }
}
```

| Situation                          | Status | Error Code                 |
| ---------------------------------- | ------ | -------------------------- |
| Request body invalid               | 400    | `VALIDATION_FAILED`        |
| Missing or expired access token    | 401    | `UNAUTHENTICATED`          |
| Wrong role                         | 403    | `INSUFFICIENT_ROLE`        |
| Plan tier does not include feature | 403    | `ENTITLEMENT_REQUIRED`     |
| Not the owner of the resource      | 403    | `NOT_RESOURCE_OWNER`       |
| Entity not found                   | 404    | `*_NOT_FOUND`              |
| Business rule violated             | 409    | domain-specific code       |
| Rate limit exceeded                | 429    | `RATE_LIMIT_EXCEEDED`      |
| Anything else                      | 500    | `INTERNAL_ERROR`           |

- Never catch an exception only to rethrow it unchanged
- Never use an empty catch block
- Never expose a stack trace, SQL fragment, or raw exception message in a response
- Every `ErrorCode` is an enum constant with a human-readable message — never a string literal at the throw site
- Log with a bracketed context prefix: `log.error("[github/verify] PR fetch failed for task {}", taskId, ex)`

---

## Transactions

- `@Transactional` on the service method, never wider
- Never make an outbound HTTP call inside a transaction. Fetch first, then open the transaction, or use `TransactionSynchronizationManager.afterCommit` for side effects
- Notification dispatch and audit-log side effects that must not roll back the main operation go through `afterCommit`
- Use `@Transactional(propagation = REQUIRES_NEW)` only for audit and webhook-event recording that must survive a rollback, and comment why
- **Never use `@Version` optimistic locking on a hot counter.** Under launch-day load every concurrent write after the first retries, and the retry loop is the bottleneck. Use a conditional atomic update instead, which is race-free and needs no retry:

```java
// Correct — capacity enforced by the database, one statement, no retry
@Modifying
@Query(value = """
    UPDATE batches SET enrolled_count = enrolled_count + 1
     WHERE id = :batchId AND enrolled_count < capacity
    """, nativeQuery = true)
int tryReserveSeat(@Param("batchId") Long batchId);
// returns 0 → batch is full
```

- `@Version` is still correct for low-contention entities edited by one user at a time (`payroll_records`, `requirement_documents`) — the distinction is contention, not importance
- Never rely on `@Transactional` on a private or self-invoked method — it does nothing

---

## Security Rules

- BCrypt cost factor 12 for passwords. Configured once in `SecurityConfig`, never inline.
- Refresh tokens stored as SHA-256 hashes. The raw token exists only in the response body.
- Never log a token, password, OTP, webhook secret, card detail, or full phone number
- Every webhook endpoint verifies its HMAC signature against the raw request body **before** parsing JSON
- Rate limiting at 60 requests/minute/IP via a Redis-backed filter; auth endpoints at 10/minute/IP
- All queries go through JPA or parameterised `@Query`. String-concatenated SQL is a defect.
- File uploads: validate content type and magic bytes, cap at 10MB, generate a server-side filename — never trust the client filename
- CORS allow-list comes from configuration, never `*`
- `@PreAuthorize` on every non-public controller method. A missing annotation is a defect, not an oversight.

---

## Constants

Every threshold, limit, and magic value lives in `common/util/Constants.java` or in a config table. Never inline.

```java
public final class Constants {
    private Constants() {}

    public static final int PIP_REMEDIATION_DAYS = 15;
    public static final int QUIZ_PASS_PERCENTAGE = 60;
    public static final int PIP_CLEARANCE_TASK_PERCENT = 85;
    public static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    public static final String INVOICE_PREFIX = "MSH-INV";
    public static final String CERTIFICATE_PREFIX = "MSH-CERT";
}
```

PIP numeric thresholds (75% attendance, ≥1 overdue-task count, 60% quiz) are **not** constants — they are rows in `pip_rules` and are read at evaluation time by feature 17's rule evaluator. Hardcoding them in Java is a defect.

This governs the *decision* threshold each rule compares against (e.g. `PROJECT_DELAY`'s "≥ 1 overdue task"), not the *measurement window* baked into a `student_metrics` column's own definition (e.g. `tasks_overdue_48h`'s 48-hour cutoff, `attendance_percent`'s rolling 14-day window) — those are schema-fixed by architecture.md's V9 table and read from `common/util/Constants.java`, not `pip_rules`, because `pip_rules` doesn't exist until feature 17's V11 and `student_metrics` is refreshed a full nightly job earlier (see progress-tracker.md's feature 16 decision log for the full reconciliation, mirroring how the token_version-cache and exponential-backoff wording was reconciled after features 03/04 and 08).

---

## Database Rules

- Flyway owns the schema. `ddl-auto: validate`. Every entity change ships with a migration in the same commit.
- Never edit a migration that has been applied anywhere. Add `V{n+1}`.
- Every foreign key has an index. Every column used in a `WHERE` or `ORDER BY` on a large table has an index.
- Composite indexes follow query order: `(batch_id, status, created_at)` not `(created_at, status, batch_id)`
- Money is `DECIMAL(12,2)` in MySQL and `BigDecimal` in Java. Never `DOUBLE`, never `double`.
- Enums are `VARCHAR` with a `CHECK` constraint. Never MySQL `ENUM` — altering it is a table rebuild.
- Soft delete only where audit requires it (`users`, `certificates`). Everything else hard-deletes or transitions status.
- Seeders live in `V{n}__seed_*.sql` and are idempotent (`INSERT ... ON DUPLICATE KEY UPDATE`). **Seed migrations carry reference data only** — roles, plans, `pip_rules`. `permissions`/`role_permissions` are created by V1 but are **not** seeded — RBAC in this build is role-based (`hasRole`) throughout; see the decision in `progress-tracker.md`. Sample users, batches, and leads live in `db/testdata/` and are loaded by a dev-profile runner. A seeder that creates fake students will run in production.

### Conditional Uniqueness

**MySQL has no partial unique indexes.** `CREATE UNIQUE INDEX ... WHERE` is PostgreSQL syntax and will not parse. Conditional uniqueness uses a `STORED` generated column that is `NULL` when the condition does not hold — MySQL permits unlimited `NULL`s in a unique index:

```sql
-- One ACTIVE subscription per user
status          VARCHAR(20)     NOT NULL,
active_user_id  BIGINT UNSIGNED
    GENERATED ALWAYS AS (IF(status = 'ACTIVE', user_id, NULL)) STORED,
UNIQUE KEY uq_one_active_subscription (active_user_id)
```

Two exist in this schema: one active subscription per user, and one open PIP record per user. Both are correctness guarantees, not conveniences — losing either allows double-billing or duplicate PIP cycles.

### Forward-Only Migrations

Flyway Community has **no undo scripts**. Reverting a deployment reverts the application, never the schema. Any destructive change therefore uses expand/contract across three releases:

```
Release N    expand   — add the new column nullable, backfill,
                        application writes both old and new
Release N+1  migrate  — application reads only the new column
Release N+2  contract — drop the old column
```

At every point the previous application version still runs against the current schema.

- Never `DROP COLUMN`, `RENAME COLUMN`, or narrow a type in the same release as the code change
- Any migration on a table over 1M rows uses `ALGORITHM=INPLACE, LOCK=NONE` and is `EXPLAIN`-checked first
- Migrations that add constraints or indexes to populated tables are tested against a **restored production-sized dump**, never only against an empty Testcontainer

### Database Users

Two, with different grants. This is what makes the insert-only audit log real rather than aspirational.

| User             | Used by            | Grants                                                        |
| ---------------- | ------------------ | ------------------------------------------------------------- |
| `moriah_migrate` | Flyway, at startup | `ALL PRIVILEGES` on the schema — DDL required                 |
| `moriah_app`     | HikariCP runtime   | DML on all tables except `audit_logs`, where it holds `SELECT, INSERT` only. **No DDL.** |

Flyway builds a separate short-lived `DataSource` from `MIGRATE_DB_*` and closes it. The application pool never holds DDL rights. Never point both at the same credentials for convenience in dev — the grant difference is exactly what the tests need to verify.

---

## N+1 Prevention

The single most likely performance defect in this codebase.

- Any repository method returning a list that will have associations accessed must use `JOIN FETCH` or an `@EntityGraph`
- For read-only aggregates, project directly into a record — do not load entities
- Enable `spring.jpa.properties.hibernate.generate_statistics=true` in dev and check query counts when adding a list endpoint
- The PIP job reads the `student_metrics` table in one query for the whole cohort. A per-student repository call inside the loop is a defect, not an optimisation opportunity.

---

## Async and Scheduled Work

```java
@Scheduled(cron = "${moriah.pip.cron}", zone = "${moriah.pip.zone}")
@SchedulerLock(name = "pipEvaluation", lockAtMostFor = "30m")
public void evaluate() { ... }
```

- Every scheduled job is guarded by ShedLock — the app will run more than one instance
- Cron expressions come from configuration, never hardcoded
- Every job logs start, item count, and completion, and **writes a `job_runs` row**. A job that silently did nothing must be diagnosable the next morning without a debugger.
- **The nightly chain is ordered and the ordering is load-bearing:**

```
01:30  AttendanceFinalisationJob  → writes ABSENT rows for no-shows
01:45  MetricsRefreshJob          → recomputes student_metrics
02:00  PipEvaluationJob           → reads student_metrics, applies rules
```

  Metrics computed before absences are written are wrong — the attendance denominator only counts students who checked in, so a student who never attends reads as 100%. Never reorder these, never collapse them into one job, and never widen a job's cron so the windows overlap.

- Jobs that iterate a cohort read one flat table and process in memory. A repository call inside the per-item loop is a defect regardless of how small the cohort is today
- `@Async` methods return `void` or `CompletableFuture` and have their own try/catch — an exception in an async method is otherwise silently swallowed

---

## Testing

- Every service method with a branch has a unit test with Mockito
- Every controller has an integration test with `@SpringBootTest` + Testcontainers MySQL + REST Assured
- Every role-restricted endpoint has a test asserting `403` for a wrong role
- Webhook idempotency has an explicit test: same event id twice produces one subscription
- The PIP engine has a test per rule with a fixture that sits just above and just below the threshold
- Never use `@MockBean` in a test that could use a real Testcontainers dependency
- No test depends on another test's data or execution order
- `mvn verify` must pass before a feature is marked complete in progress-tracker.md

---

## Logging

- SLF4J via Lombok `@Slf4j`. Never `System.out.println`.
- Bracketed context prefix on every log: `[module/operation]`
- `INFO` for lifecycle and business milestones, `WARN` for handled failures and rejected input, `ERROR` for unexpected failures with the exception attached
- Never log request bodies containing PII, credentials, or payment data
- Every inbound webhook logs its event id and type at `INFO` — this is the audit trail when a gateway disputes delivery

---

## Comments and Javadoc

- No comments explaining what the code does — the code must be self-explanatory
- Comments only for why: a non-obvious business rule, a gateway quirk, a deliberate deviation
- Javadoc on public service interfaces and on any method implementing a rule from the FRS — cite the requirement id, e.g. `MSH-FR-STU-06`
- Never leave a `TODO` in committed code. Unfinished work goes in progress-tracker.md.

---

## Dependencies

Never add a dependency without a clear reason. Before adding anything check:

1. Does Spring Boot already provide this via a starter?
2. Does the JDK 21 standard library already do it?
3. Is there a simpler solution with what is already here?

Approved dependencies:

- `spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation`, `-data-redis`, `-oauth2-client`, `-oauth2-resource-server`, `-mail`, `-actuator`
- `mysql-connector-j` — JDBC driver
- `flyway-core`, `flyway-mysql` — migrations
- `org.projectlombok:lombok` — boilerplate reduction
- `org.mapstruct:mapstruct` + processor — DTO mapping
- `io.jsonwebtoken:jjwt-api/impl/jackson` — JWT
- `software.amazon.awssdk:s3` — object storage
- `com.razorpay:razorpay-java` — Razorpay
- `com.stripe:stripe-java` — Stripe
- `com.github.librepdf:openpdf` — PDF generation
- `com.google.zxing:core` + `javase` — QR codes
- `org.apache.poi:poi-ooxml` — XLSX export
- `org.springdoc:springdoc-openapi-starter-webmvc-ui` — OpenAPI 3
- `net.javacrumbs.shedlock:shedlock-spring` + `-provider-jdbc-template` — scheduler locking
- `com.sendgrid:sendgrid-java` — email
- Test: `spring-boot-starter-test`, `org.testcontainers:mysql`, `io.rest-assured:rest-assured`

Do not add any other dependency without updating this list first.
