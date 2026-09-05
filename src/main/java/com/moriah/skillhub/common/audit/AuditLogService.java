package com.moriah.skillhub.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.security.ClientIpResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Records every financial, grade, role, and PIP-status mutation, plus failed logins
 * (code-standards.md / build-plan.md feature 03). {@code ipAddress}/{@code userAgent} are read
 * from the current request rather than taken as parameters, so every call site matches the
 * simple 6-argument signature in code-standards.md's {@code SprintService} example.
 * <p>
 * {@code REQUIRES_NEW}: the audit trail must survive a rollback of the transaction that
 * triggered it (code-standards.md "Transactions") — an audited attempt that failed for an
 * unrelated reason later in the same transaction should still be visible in the trail, not
 * silently disappear with it.
 * <p>
 * <b>Security audit (2026-09-05):</b> {@code ipAddress} now goes through {@link ClientIpResolver}
 * — the same trusted-proxy-aware resolution {@code RateLimitFilter} already uses — instead of a
 * bare {@code request.getRemoteAddr()}. Behind any real reverse proxy or load balancer, the raw
 * remote address is the proxy's own IP, not the caller's; every audit row this service has ever
 * written was recording the wrong IP address in that topology (NFR-03: "audit... security login
 * events" implies a real client IP, not the LB's). {@code AuthController} already used the
 * resolver correctly for its own directly-written rows; this closes the gap for every other
 * mutation this service audits.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String action, String entityType, Long entityId, Object oldValue, Object newValue) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setOldValue(toJson(oldValue));
        auditLog.setNewValue(toJson(newValue));

        currentRequest().ifPresent(request -> {
            auditLog.setIpAddress(clientIpResolver.resolve(request));
            auditLog.setUserAgent(request.getHeader("User-Agent"));
        });

        auditLogRepository.save(auditLog);
    }

    /**
     * Use instead of {@link #record} whenever {@code userId} (or anything else this row's {@code
     * fk_audit_logs_user} FK will need to lock) is a row the CALLER's own still-open transaction
     * just inserted or updated — {@code record}'s {@code REQUIRES_NEW} opens a second, concurrent
     * transaction that must take a lock on that exact row to satisfy the FK check, and the caller's
     * transaction won't release it until this call returns. Neither side is blocked on a DB
     * resource the other can see, so InnoDB can't detect the cycle as a deadlock — it just sits
     * for the full {@code innodb_lock_wait_timeout} (~50s) and then fails the whole request.
     * Confirmed the hard way against {@code ClientRegistrationService#register}: inserts a brand
     * new {@code users} row, then audited it inline in the same transaction — every single
     * self-registration timed out. Same {@code TransactionSynchronizationManager.
     * registerSynchronization(...).afterCommit()} idiom {@code NotificationService.
     * enqueueAfterCommit} already uses for the identical "must not race the caller's own
     * transaction" reason. Request context (ip/user-agent) is captured eagerly, before the
     * callback — {@code RequestContextHolder} may no longer be valid by the time it runs.
     */
    public void recordAfterCommit(Long userId, String action, String entityType, Long entityId,
                                   Object oldValue, Object newValue) {
        String ipAddress = currentRequest().map(clientIpResolver::resolve).orElse(null);
        String userAgent = currentRequest().map(r -> r.getHeader("User-Agent")).orElse(null);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recordWithContext(userId, action, entityType, entityId, oldValue, newValue, ipAddress, userAgent);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordWithContext(Long userId, String action, String entityType, Long entityId,
                            Object oldValue, Object newValue, String ipAddress, String userAgent) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setOldValue(toJson(oldValue));
        auditLog.setNewValue(toJson(newValue));
        auditLog.setIpAddress(ipAddress);
        auditLog.setUserAgent(userAgent);
        auditLogRepository.save(auditLog);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("[audit/serialize] failed to serialize audit value, storing null", e);
            return null;
        }
    }

    private Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return Optional.of(attrs.getRequest());
        }
        return Optional.empty();
    }
}
