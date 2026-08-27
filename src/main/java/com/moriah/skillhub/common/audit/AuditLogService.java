package com.moriah.skillhub.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

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
            auditLog.setIpAddress(request.getRemoteAddr());
            auditLog.setUserAgent(request.getHeader("User-Agent"));
        });

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
