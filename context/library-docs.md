# Library Docs

Project-specific usage patterns for every third party library and external API in this backend. This file only covers how we use each one in Moriah Skill Hub — rules, patterns, and constraints specific to this project.

Read the relevant section before implementing any feature that touches these libraries.

---

## Before Using Any Library

1. **Check AGENTS.md** at the project root — it lists every skill installed for this project and how to use them.
2. **Check whether an MCP server is configured** for that library. If one is available, use it before falling back to general knowledge.
3. **Read this file** for project-specific patterns that override general library knowledge.

Order of authority:

```
MCP server (real-time docs) → Skills via AGENTS.md → This file (project rules) → General training knowledge
```

Spring Security 6, Spring Data JPA 3, and the AWS SDK v2 all changed significantly from the versions most training data covers. Never rely on remembered API shapes for these three.

---

## Spring Security 6

### Filter Chain

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity                       // enables @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)          // stateless JWT API
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/plans/**").permitAll()
                .requestMatchers("/api/v1/certificates/verify/**").permitAll()
                .requestMatchers("/api/v1/webhooks/**").permitAll()   // signature-verified, not token-verified
                .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

**Rules:**

- `WebSecurityConfigurerAdapter` does not exist in Spring Security 6 — never extend it
- `authorizeRequests()` and `antMatchers()` are removed — use `authorizeHttpRequests()` and `requestMatchers()`
- Webhook paths are `permitAll()` at the filter level and secured by HMAC signature inside the controller — this is deliberate, gateways cannot present a JWT
- `@EnableMethodSecurity` replaces `@EnableGlobalMethodSecurity`
- Roles in `@PreAuthorize("hasRole('X')")` must be stored as `ROLE_X` authorities — the `hasRole` helper adds the prefix

### Current User Resolution

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "userId")
public @interface CurrentUser {}
```

Always resolve the caller from the `SecurityContext`. Never accept a user id from a request body or path variable for the caller's own identity — that is an authorization bypass.

---

## JJWT

```java
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties props;

    public String generateAccessToken(User user, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUuid())
                .claim("roles", roles)
                .claim("tv", user.getTokenVersion())
                .id(UUID.randomUUID().toString())               // jti, for revocation
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTokenMinutes(), ChronoUnit.MINUTES)))
                .header().keyId(props.keyId()).and()
                .signWith(key(props.keyId()), Jwts.SIG.HS512)
                .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token);
    }
}
```

**Rules:**

- JJWT 0.12+ API: `signWith(key, Jwts.SIG.HS512)`, `parseSignedClaims()`. The older `setSubject`/`parseClaimsJws` builders are deprecated — do not use them.
- `sub` is the user **uuid**, never the numeric id
- Access token 60 minutes, refresh token 30 days — both from `JwtProperties`, never literals
- **`tier` is never a claim.** A cached tier goes stale the moment a user upgrades, and stale entitlement is an authorization bug. `EntitlementGuard` reads the DB per check, cached 60s.
- **`tv` (token_version) is a mandatory claim.** `JwtAuthFilter` compares it against `users.token_version` (Redis-cached, 60s TTL) and rejects on mismatch. Suspension, password reset, role change, and `logout-all` increment the column, so revocation lands within a second instead of waiting out the 60-minute expiry. A stateless JWT with no version check means "suspend user" does nothing for up to an hour — that was a real defect in an earlier draft of this design.
- **Set a `kid` header** so `JWT_SECRET` can be rotated with an overlap window. Without it, rotating the secret logs out every user simultaneously.
- Refresh rotation: on every refresh, mark the old token `revoked_at` and issue a new one with `replaced_by` set. If a token that already has `replaced_by` is presented, revoke the whole chain and force re-login — that is token theft.
- Never put PII, email, or phone in a JWT claim

---

## Spring Data JPA + Hibernate 6

### Fetching

```java
// Correct — one query
@Query("""
    SELECT t FROM Task t
    JOIN FETCH t.sprint s
    JOIN FETCH t.assignedTo u
    WHERE s.batch.id = :batchId AND t.status = :status
    """)
List<Task> findForReview(@Param("batchId") Long batchId, @Param("status") TaskStatus status);

// Correct — projection off the refreshed metrics table, no entities loaded.
// This is the ONE query the PIP job runs for the entire cohort.
public record StudentMetricRow(Long userId, Long batchId,
                               BigDecimal attendancePercent,
                               BigDecimal taskCompletionPercent,
                               BigDecimal quizAveragePercent,
                               Integer tasksOverdue48h,
                               Integer daysSinceLastActivity,
                               Integer consecutiveAssignmentsMissed,
                               Integer unsatisfactoryReviews) {}

@Query(value = """
    SELECT sm.user_id, sm.batch_id, sm.attendance_percent,
           sm.task_completion_percent, sm.quiz_average_percent,
           sm.tasks_overdue_48h, sm.days_since_last_activity,
           sm.consecutive_assignments_missed, sm.unsatisfactory_reviews
      FROM student_metrics sm
      JOIN batch_students bs
        ON bs.user_id = sm.user_id AND bs.batch_id = sm.batch_id
     WHERE bs.status = 'ACTIVE'
    """, nativeQuery = true)
List<StudentMetricRow> loadAllActiveMetrics();
```

**Rules:**

- `open-in-view: false` is set. Any lazy association touched outside a service transaction throws — fix the query, never the setting.
- `JOIN FETCH` with `Pageable` causes in-memory pagination and a Hibernate warning. Use `@EntityGraph` with pagination, or two queries (ids page, then fetch).
- Never call a repository inside a loop. Use `findAllById`, an `IN` query, or a view.
- `saveAll` with `hibernate.jdbc.batch_size=50` for bulk inserts; flush and clear the persistence context every 50 rows in long loops
- The PIP cohort read goes against the refreshed `student_metrics` **table**, not a view — MySQL 8 does not materialize views, and stacked aggregate views re-scan full history on every call
- The three small admin-facing views that remain (`v_batch_velocity`, `v_revenue_monthly`, `v_lead_funnel`) are read with `nativeQuery = true`, mapped to record projections
- Admin exports and heavy metrics reads target the **read replica**, never the primary

### Auditing

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
```

Requires `@EnableJpaAuditing` in `JpaConfig`. This handles timestamps only — business audit trails go to `audit_logs` via `AuditLogService`, which is a different concern.

---

## Flyway

```sql
-- V3__batches_sprints_tasks.sql

CREATE TABLE batches (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name           VARCHAR(150)    NOT NULL,
    track_code     VARCHAR(50)     NOT NULL,
    pm_id          BIGINT UNSIGNED NOT NULL,
    start_date     DATE            NOT NULL,
    end_date       DATE            NOT NULL,
    capacity       SMALLINT        NOT NULL DEFAULT 30,
    enrolled_count SMALLINT        NOT NULL DEFAULT 0,
    version        INT             NOT NULL DEFAULT 0,
    status         VARCHAR(20)     NOT NULL DEFAULT 'PLANNED',
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_batches_pm FOREIGN KEY (pm_id) REFERENCES users (id),
    CONSTRAINT chk_batches_status CHECK (status IN ('PLANNED','ACTIVE','COMPLETED','CANCELLED')),
    INDEX idx_batches_pm_status (pm_id, status),
    INDEX idx_batches_dates (start_date, end_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
```

**Rules:**

- Naming: `V{n}__{snake_case_description}.sql`, two underscores
- One logical concern per migration file
- **Versions ascend with feature numbers.** V1–V5 at feature 02, V6–V8 at 06, V9 at 16, and so on to V15 at 23. A migration added mid-build takes the next unused number — never one inserted between existing versions, which changes nothing for Flyway but makes the ledger unreadable.
- Never modify an applied migration — Flyway checksums it and startup will fail. Add a new version.
- **Flyway Community has no undo scripts.** There is no `flyway undo`. Every migration is forward-only, and any destructive change goes through expand/contract across three releases. Never write a migration on the assumption it can be reversed.
- **Conditional uniqueness needs a generated column** — `CREATE UNIQUE INDEX ... WHERE` is PostgreSQL and will not parse on MySQL:

```sql
open_user_id BIGINT UNSIGNED
    GENERATED ALWAYS AS (
        IF(status IN ('TRIGGERED','IN_PROGRESS'), user_id, NULL)
    ) STORED,
UNIQUE KEY uq_one_open_pip (open_user_id)
```

- Flyway runs on its own `DataSource` as `moriah_migrate`, configured via `spring.flyway.user` / `spring.flyway.password`, separate from the runtime pool
- Every FK gets an explicit `CONSTRAINT fk_{table}_{column}` name — MySQL's generated names are unusable in a rollback
- Every status/type column gets a `CHECK` constraint listing valid values, matching the Java enum exactly
- `DATETIME(6)` for timestamps to match `Instant` precision
- Seed data goes in its own migration and uses `INSERT ... ON DUPLICATE KEY UPDATE` so re-runs are safe
- Views live in `V9__reporting_views.sql` and are `CREATE OR REPLACE VIEW`

---

## MapStruct

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SprintMapper {

    SprintResponse toResponse(Sprint sprint);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "batch", ignore = true)
    @Mapping(target = "status", ignore = true)
    Sprint toEntity(CreateSprintRequest request);
}
```

**Rules:**

- `componentModel = "spring"` always — the mapper is an injectable bean
- `unmappedTargetPolicy = ERROR` — a forgotten field must break the build, not silently produce null
- Never hand-write a mapper. Never map inside a service method.
- Associations are set in the service after mapping, never by the mapper
- Lombok must be declared **before** MapStruct in the annotation processor path or generated getters will not be visible

---

## Razorpay

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayService {

    private final RazorpayProperties props;

    public Order createOrder(BigDecimal amountInr, String receipt) throws RazorpayException {
        RazorpayClient client = new RazorpayClient(props.keyId(), props.keySecret());
        JSONObject request = new JSONObject()
                .put("amount", amountInr.multiply(BigDecimal.valueOf(100)).intValueExact())  // paise
                .put("currency", "INR")
                .put("receipt", receipt)
                .put("payment_capture", 1);
        return client.orders.create(request);
    }

    public boolean verifySignature(String rawBody, String signatureHeader) {
        try {
            return Utils.verifyWebhookSignature(rawBody, signatureHeader, props.webhookSecret());
        } catch (RazorpayException e) {
            log.warn("[razorpay/webhook] signature verification failed");
            return false;
        }
    }
}
```

**Rules:**

- Razorpay amounts are in **paise**. Always `amount * 100` and always `intValueExact()` — a rounding error here is a financial defect.
- The signature must be verified against the **raw request body bytes**, before Jackson parses anything. Capture it with a `ContentCachingRequestWrapper` or `@RequestBody String`.
- `receipt` is our `payments.gateway_order_id` reference — always set it, it is the reconciliation key
- Never trust the amount in the webhook payload. Re-read the plan price from the DB and compare.
- Return `200` to Razorpay even for a duplicate event — a non-200 triggers their retry storm

---

## Stripe

```java
Stripe.apiKey = props.secretKey();

SessionCreateParams params = SessionCreateParams.builder()
        .setMode(SessionCreateParams.Mode.PAYMENT)
        .setSuccessUrl(props.successUrl())
        .setCancelUrl(props.cancelUrl())
        .setClientReferenceId(payment.getId().toString())
        .addLineItem(SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(/* amount in smallest currency unit */)
                .build())
        .build();

// Webhook
Event event = Webhook.constructEvent(rawBody, sigHeader, props.webhookSecret());
```

**Rules:**

- `Webhook.constructEvent` both verifies and parses — never parse the body yourself first
- Stripe amounts are in the smallest currency unit (cents/paise) as `Long`
- `client_reference_id` carries our `payments.id` — that is how the webhook finds the record
- Handle `checkout.session.completed` for activation and `charge.refunded` for refunds. Ignore all other event types explicitly with a logged `INFO`.

---

## Webhook Idempotency

Both gateways retry. This pattern is mandatory for every webhook endpoint.

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean claim(String gateway, String eventId, String eventType, String payload) {
    try {
        webhookEventRepository.save(new WebhookEvent(gateway, eventId, eventType, payload));
        return true;                    // first time — proceed
    } catch (DataIntegrityViolationException e) {
        log.info("[webhook/{}] duplicate event {} ignored", gateway, eventId);
        return false;                   // already processed — stop
    }
}
```

**Rules:**

- The unique constraint on `webhook_events.event_id` is the idempotency guarantee. Never replace it with a `SELECT` then `INSERT` — that races.
- `REQUIRES_NEW` so the claim survives a rollback of the business transaction
- Never process a webhook outside this guard
- GitHub and WhatsApp webhooks use the same table and the same pattern

---

## GitHub REST API

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubVerificationService {

    private final RestClient gitHubClient;   // pre-configured with token + Accept header

    private static final Pattern PR_URL =
        Pattern.compile("^https://github\\.com/([\\w.-]+)/([\\w.-]+)/pull/(\\d+)/?$");

    public PrVerification verify(String prUrl, String expectedAuthor) {
        Matcher m = PR_URL.matcher(prUrl.trim());
        if (!m.matches()) {
            throw new BusinessException(ErrorCode.INVALID_PR_URL);
        }
        String owner = m.group(1), repo = m.group(2), number = m.group(3);

        try {
            PullRequestDto pr = gitHubClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, number)
                    .retrieve()
                    .body(PullRequestDto.class);

            if (!pr.user().login().equalsIgnoreCase(expectedAuthor)) {
                throw new BusinessException(ErrorCode.PR_AUTHOR_MISMATCH);
            }
            return new PrVerification(owner, repo, Integer.parseInt(number),
                                      pr.state(), pr.commits(), pr.head().sha());
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(ErrorCode.PR_NOT_FOUND);
        }
    }
}
```

**Rules:**

- Always send `Accept: application/vnd.github+json` and `X-GitHub-Api-Version: 2022-11-28`
- Authenticated rate limit is 5,000 requests/hour. Cache verification results in Redis for 5 minutes keyed by `owner/repo/pr` — a batch of 30 students refreshing a page must not burn 30 calls.
- Always verify the PR author matches `users.github_username`. Without this check a student can submit someone else's PR.
- Never clone, never download the diff. We verify existence and metadata only.
- A GitHub outage must not block submission. On a 5xx, persist the submission with `verified_at = null` and status `SUBMITTED`, and let a retry job verify later.

---

## AWS SDK v2 (S3 / R2)

```java
@Bean
S3Client s3Client(StorageProperties props) {
    S3ClientBuilder builder = S3Client.builder()
            .region(Region.of(props.region()))
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey(), props.secretKey())));

    if (StringUtils.hasText(props.endpoint())) {
        builder.endpointOverride(URI.create(props.endpoint()))   // Cloudflare R2
               .serviceConfiguration(S3Configuration.builder()
                       .pathStyleAccessEnabled(true).build());
    }
    return builder.build();
}
```

```java
public String upload(String key, byte[] content, String contentType) {
    s3Client.putObject(
            PutObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(key)
                    .contentType(contentType)
                    .build(),
            RequestBody.fromBytes(content));
    return key;
}

public URL presignedGetUrl(Long userId, String key, Duration ttl) {
    // MANDATORY — signing a key because the caller asked for it is an IDOR
    ownershipGuard.requireKeyAccess(userId, key);

    return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(b -> b.bucket(props.bucket()).key(key))
            .build()).url();
}
```

**Storage key layout:**

```
resumes/{userUuid}/resume.pdf
certificates/{certificateNumber}.pdf
invoices/{invoiceNumber}.pdf
payslips/{employeeCode}/{YYYY-MM}.pdf
projects/{projectId}/assets/{assetId}-{filename}
submissions/{submissionId}/{filename}
hr-documents/{userUuid}/{documentType}-{uuid}.pdf
```

**Rules:**

- SDK v2 only. `AmazonS3ClientBuilder` is v1 and is not on the classpath.
- The bucket is **private**. Never `PublicRead`. All access is via presigned URLs with a 15-minute TTL, generated on request.
- **Every presigned URL is authorized before signing.** The key layout embeds the owner's uuid or a resource id precisely so `OwnershipGuard` can check it. A method that signs an arbitrary caller-supplied key is a defect, not a convenience.
- Store the **key** in the database, never a full URL — URLs expire and the endpoint may change
- Never write to local disk. Upload the byte array or stream directly.
- Setting `S3_ENDPOINT` switches to Cloudflare R2 with no code change — keep it that way
- Presigned URL generation is not a mutation. Never generate one inside a write transaction.

---

## Redis

Three distinct uses, three key namespaces. Never mix them.

```
cache:{entity}:{id}              → Spring Cache abstraction, TTL 10m
ratelimit:{ip}:{window}          → rate limiting counters, TTL 1m
queue:notifications              → pending notification dispatch list
queue:notifications:processing   → in-flight list, for redelivery on worker death
tokenversion:{userId}            → token_version cache, TTL 60s
denylist:jti:{jti}               → revoked token ids, TTL = remaining token life
github:pr:{owner}/{repo}/{num}   → PR verification cache, TTL 5m
```

### Reliable Queue

A plain `LPOP` loses the message if the worker dies between pop and dispatch. Use `RPOPLPUSH` into a processing list and remove only after a confirmed send:

```java
String payload = redis.opsForList()
        .rightPopAndLeftPush("queue:notifications", "queue:notifications:processing",
                             Duration.ofSeconds(5));
if (payload == null) return;
try {
    dispatch(payload);
    redis.opsForList().remove("queue:notifications:processing", 1, payload);  // ack
} catch (Exception e) {
    // leave it in processing; the reaper requeues entries older than 5 minutes
    log.warn("[notify] dispatch failed, will be redelivered", e);
}
```

A reaper sweeps `queue:notifications:processing` and requeues anything stale. Notifications carry PIP triggers and payment confirmations — silently dropping one is not acceptable.

```java
@Cacheable(value = "plans", key = "'active'")
public List<PlanResponse> listActivePlans() { ... }

@CacheEvict(value = "plans", allEntries = true)
public PlanResponse update(Long id, UpdatePlanRequest request) { ... }
```

**Rules:**

- Cache only genuinely read-heavy, rarely-changing data: subscription plans, roles, `pip_rules`. (`permissions` is not cached because it is never queried — it holds no rows; see `progress-tracker.md` for why.)
- **Never cache** anything student-scoped, financial, or PIP-status related. Stale data there is a correctness bug.
- Every `@Cacheable` has a matching `@CacheEvict` on the corresponding write path — write one without the other and the cache goes stale silently
- Redis is a cache, not a store. Losing Redis must degrade performance, never lose data.
- Use `StringRedisSerializer` for keys and JSON for values — the default JDK serializer produces unreadable keys

---

## Notification Dispatch

Enqueue in the request, send in the worker. Never send inline.

```java
// In a service, after commit
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override public void afterCommit() {
            notificationService.enqueue(userId, Channel.WHATSAPP, "PIP_TRIGGERED", payload);
        }
    });
```

**Rules:**

- A notification failure must never roll back the business transaction that caused it — hence `afterCommit`
- The worker retries up to 3 times with exponential backoff, then marks `FAILED` with the error
- WhatsApp Cloud API only accepts pre-approved template codes outside the 24-hour session window. Store `template_code`, never free text, for outbound-initiated messages.
- Every notification writes a `notifications` row before dispatch — the row is the audit record, sending is the side effect
- Never send a notification for an event that has not been persisted

---

## OpenPDF + ZXing

```java
public byte[] generateCertificate(Certificate cert) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        Document doc = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(doc, out);
        doc.open();
        // layout
        String verifyUrl = props.baseUrl() + "/verify/" + cert.getVerificationCode();
        doc.add(Image.getInstance(qrCodeService.png(verifyUrl, 150)));
        doc.close();
        return out.toByteArray();
    } catch (Exception e) {
        throw new BusinessException(ErrorCode.CERTIFICATE_GENERATION_FAILED);
    }
}
```

**Rules:**

- The QR encodes a **URL to the public verification endpoint**, never the certificate data itself
- `verification_code` is a 12-character cryptographically random string, unique, and is the only value the public endpoint accepts — never accept a certificate id
- Generate the PDF, upload to S3, store the key, then return. Never regenerate on every download.
- The verification endpoint is unauthenticated and returns only: holder name, batch, certificate type, issue date, validity. Never email, phone, or scores.
- A revoked certificate returns `valid: false` with the revocation date — never a 404

---

## Apache POI

```java
try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {   // streaming, 100 rows in memory
    Sheet sheet = workbook.createSheet("Students");
    // write rows
    workbook.write(outputStream);
    workbook.dispose();
}
```

**Rules:**

- `SXSSFWorkbook` for exports, not `XSSFWorkbook` — an admin exporting 50,000 rows must not exhaust heap
- Always `dispose()` to clean temp files
- Exports run `@Async` and deliver via a presigned S3 URL for anything over 1,000 rows — never block an HTTP request on a large export
- Format money cells with a data format, never as pre-formatted strings — accountants filter and sum these

---

## springdoc-openapi

```java
@Operation(summary = "Create a sprint for a batch")
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "Sprint created"),
    @ApiResponse(responseCode = "403", description = "Caller is not the batch PM"),
    @ApiResponse(responseCode = "409", description = "Sprint number already used")
})
```

**Rules:**

- Every controller has `@Tag`. Every endpoint has `@Operation` with a summary.
- Document every non-200 status the endpoint can actually return — the frontend builds error handling from this
- Swagger UI is enabled in `dev` and disabled in `prod` via profile configuration
- The generated spec is the contract with the frontend track. If an endpoint shape changes, tell them before merging — three engineers are coding against it.

---

## Testcontainers

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
abstract class IntegrationTestBase {

    @Container
    @ServiceConnection                      // Spring Boot 3.1+ wires the datasource automatically
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Container
    @ServiceConnection
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
}
```

**Rules:**

- `@ServiceConnection` replaces `@DynamicPropertySource` in Spring Boot 3.1+ — use it
- Containers are `static` so they start once per class hierarchy, not per test
- Flyway runs against the container — this is how migrations are validated. A broken migration must fail the build.
- Never use H2. Its MySQL compatibility mode does not support the `CHECK` constraints, JSON columns, or views this schema depends on.
- Each test seeds its own data and cleans up, or runs inside `@Transactional` with rollback
