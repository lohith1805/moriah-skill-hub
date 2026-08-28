package com.moriah.skillhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationReaperJob;
import com.moriah.skillhub.common.notification.NotificationService;
import com.moriah.skillhub.common.notification.NotificationStatus;
import com.moriah.skillhub.common.notification.QueueEnvelope;
import com.moriah.skillhub.common.notification.entity.Notification;
import com.moriah.skillhub.common.notification.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * build-plan.md feature 08 verify line: "Kill the worker mid-dispatch and confirm the message is
 * redelivered, not lost." {@code IN_APP} is used throughout, deliberately — its dispatcher is a
 * genuine no-op with zero external dependencies, so these tests exercise the real queue
 * mechanics ({@code NotificationWorker} is always running, {@code @EnableScheduling} is
 * project-wide) without ever risking a real outbound call to SendGrid/WhatsApp with the
 * test-only fake credentials {@code application-test.yml} configures. Dispatch logic itself
 * (what {@code EmailDispatcher}/{@code WhatsAppDispatcher} build and send) is covered in
 * isolation by their own mocked-client unit tests.
 */
class NotificationQueueIT extends IntegrationTestBase {

    private static final String QUEUE_KEY = "queue:notifications";
    private static final String PROCESSING_KEY = "queue:notifications:processing";

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationReaperJob reaperJob;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Same reasoning as PaymentWebhookFlowIT — the live worker dispatches on a separate thread
    // with its own transaction, so this class can't use @Transactional rollback either. Real
    // committed rows are tracked and deleted explicitly instead of leaking into the shared
    // container.
    private final List<Long> insertedNotificationIds = new ArrayList<>();
    private final List<Long> insertedUserIds = new ArrayList<>();

    @AfterEach
    void cleanUpNotifications() {
        notificationRepository.deleteAllByIdInBatch(insertedNotificationIds);
        insertedNotificationIds.clear();
        if (!insertedUserIds.isEmpty()) {
            jdbcTemplate.batchUpdate("DELETE FROM users WHERE id = ?",
                    insertedUserIds.stream().map(id -> new Object[]{id}).toList());
            insertedUserIds.clear();
        }
    }

    @Test
    void enqueue_writesQueuedRow_andTheLiveWorkerEventuallyMarksItSent() {
        long userId = insertUser();
        Notification notification = notificationService.enqueue(userId, NotificationChannel.IN_APP,
                "TEST_TEMPLATE", Map.of("foo", "bar"));
        insertedNotificationIds.add(notification.getId());

        assertThat(notification.getId()).isNotNull();
        assertThat(notification.getUserId()).isEqualTo(userId);
        assertThat(notification.getTemplateCode()).isEqualTo("TEST_TEMPLATE");

        Notification sent = waitForStatus(notification.getId(), NotificationStatus.SENT);
        assertThat(sent.getSentAt()).isNotNull();
        assertThat(sent.getAttempts()).isZero();
    }

    @Test
    void enqueue_unsupportedChannel_throwsChannelNotSupported_smsHasNoDispatcherYet() {
        long userId = insertUser();
        assertThatThrownBy(() -> notificationService.enqueue(userId, NotificationChannel.SMS, "X", Map.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NOTIFICATION_CHANNEL_NOT_SUPPORTED));
    }

    @Test
    void staleProcessingEntry_isRedeliveredByTheReaper_notLost() throws Exception {
        // A real notification, delivered normally first — establishes a real, existing row for
        // the redelivery to legitimately re-process.
        long userId = insertUser();
        Notification notification = notificationService.enqueue(userId, NotificationChannel.IN_APP,
                "REDELIVERY_TEST", Map.of());
        insertedNotificationIds.add(notification.getId());
        waitForStatus(notification.getId(), NotificationStatus.SENT);

        // Simulate "a worker RPOPLPUSHed this into processing, then died before acking" — reset
        // to QUEUED and place a stale envelope directly in the processing list, bypassing the
        // normal RPOPLPUSH path entirely (there is no way to literally kill a thread mid-dispatch
        // in a test; this reproduces the state that scenario leaves behind).
        setStatus(notification.getId(), NotificationStatus.QUEUED);
        String staleEnvelope = serialize(new QueueEnvelope(notification.getId(),
                Instant.now().minus(10, ChronoUnit.MINUTES)));
        redisTemplate.opsForList().leftPush(PROCESSING_KEY, staleEnvelope);

        reaperJob.sweep();

        assertThat(isPresentIn(PROCESSING_KEY, staleEnvelope)).isFalse();
        // Redelivered onto the main queue, then reprocessed by the always-running live worker —
        // "redelivered, not lost."
        waitForStatus(notification.getId(), NotificationStatus.SENT);
    }

    @Test
    void orphanedQueuedRow_withNoRedisEntryAtAll_isReconciledByTheReaper_notLostForever() {
        // Simulates the gap /review found: a notifications row whose Redis queue entry was lost
        // outright (Redis restarted without persistence, evicted, etc.) — not merely stuck in the
        // processing list, which staleProcessingEntry_... above already covers. Inserted directly
        // via JDBC, bypassing NotificationService entirely, so nothing is ever pushed to Redis for
        // it — and with an old created_at, since reconcileOrphanedRows() only acts on rows past
        // Constants.NOTIFICATION_ORPHAN_RECONCILE_MINUTES.
        long userId = insertUser();
        long notificationId = insertOrphanedQueuedNotification(userId);
        insertedNotificationIds.add(notificationId);

        reaperJob.sweep();

        // Re-derived and re-pushed straight from the database, then picked up and dispatched by
        // the always-running live worker — never lost forever.
        waitForStatus(notificationId, NotificationStatus.SENT);
    }

    @Test
    void freshProcessingEntry_isLeftAloneByTheReaper_notPrematurelyRedelivered() throws Exception {
        String freshEnvelope = serialize(new QueueEnvelope(999_999_999L, Instant.now()));
        redisTemplate.opsForList().leftPush(PROCESSING_KEY, freshEnvelope);

        reaperJob.sweep();

        assertThat(isPresentIn(PROCESSING_KEY, freshEnvelope)).isTrue();
        assertThat(isPresentIn(QUEUE_KEY, freshEnvelope)).isFalse();

        // Clean up — leaving a fabricated, never-real notification id sitting in the processing
        // list forever would otherwise have the live worker (or a later reaper sweep once this
        // entry does go stale) repeatedly try and fail to load it.
        redisTemplate.opsForList().remove(PROCESSING_KEY, 1, freshEnvelope);
    }

    private long insertUser() {
        String email = "notify-test-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                INSERT INTO users (uuid, full_name, email, status) VALUES (UUID(), 'Notify Test', ?, 'ACTIVE')
                """, email);
        long id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        insertedUserIds.add(id);
        return id;
    }

    /** Backdates a real, Hibernate-written row by a relative SQL interval rather than binding an
     * absolute Java {@code Instant} parameter — a raw-JDBC {@code Instant} write and Hibernate's
     * own read of the same {@code DATETIME(6)} column don't necessarily agree on timezone
     * handling (confirmed the hard way: an absolute-timestamp version of this helper landed rows
     * hours off from what {@code reconcileOrphanedRows()}'s Java-side {@code Instant.now()}
     * comparison expected). Subtracting an interval from whatever Hibernate already wrote is
     * self-consistent regardless of that discrepancy, since it never needs the two systems to
     * agree on an absolute point in time — only on how to add/subtract 20 minutes from the same
     * starting value. */
    private long insertOrphanedQueuedNotification(long userId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setTemplateCode("ORPHAN_TEST");
        notification.setStatus(NotificationStatus.QUEUED);
        notification = notificationRepository.save(notification);

        jdbcTemplate.update("""
                UPDATE notifications SET created_at = created_at - INTERVAL 20 MINUTE WHERE id = ?
                """, notification.getId());
        return notification.getId();
    }

    /** Feature 24 (Integration Testing and UAT) — real failure caught running the FULL suite,
     * not visible running this class alone: at feature-24 scale (267 IT methods, several new this
     * feature enqueuing real EMAIL notifications on top of this file's own IN_APP-only traffic —
     * {@code CheckoutFlowIT}'s invoice job, {@code MetricsAndPipChainProfilingIT}'s 300 triggered
     * PIP records each firing notification enqueues, etc.) the single shared {@code
     * NotificationWorker}/Redis queue this whole suite runs against genuinely has more real
     * traffic queued ahead of this test's own row than a 10-second poll reliably drains within,
     * even though the worker itself is healthy and this class passes every time run alone (5/5,
     * confirmed). Not a queue-mechanism bug — {@code EmailDispatcher} failing against the
     * test-only fake SendGrid key is expected and harmless (this file deliberately never uses
     * {@code EMAIL} itself, per its own class Javadoc), it just means the worker thread spends
     * real wall-clock time on other classes' failed dispatch attempts before reaching this test's
     * entry. 30 seconds is a generous, still-bounded budget for that realistic backlog. */
    private Notification waitForStatus(Long id, NotificationStatus expected) {
        long deadline = System.currentTimeMillis() + 30_000;
        Notification notification = null;
        while (System.currentTimeMillis() < deadline) {
            notification = notificationRepository.findById(id).orElseThrow();
            if (notification.getStatus() == expected) {
                return notification;
            }
            sleep();
        }
        assertThat(notification.getStatus()).isEqualTo(expected);
        return notification;
    }

    private void setStatus(Long id, NotificationStatus status) {
        Notification notification = notificationRepository.findById(id).orElseThrow();
        notification.setStatus(status);
        notificationRepository.save(notification);
    }

    private boolean isPresentIn(String listKey, String value) {
        List<String> entries = redisTemplate.opsForList().range(listKey, 0, -1);
        return entries != null && entries.contains(value);
    }

    private String serialize(QueueEnvelope envelope) throws Exception {
        return objectMapper.writeValueAsString(envelope);
    }

    private void sleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
