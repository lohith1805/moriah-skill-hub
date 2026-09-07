package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.AuditLogResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * {@code GET /admin/audit?entityType=&userUuid=&from=} — build-plan.md feature 22: "Audit log
 * read-only through the API" (this class never issues anything but {@code SELECT}, on top of
 * {@code moriah_app} already holding {@code SELECT, INSERT} only on {@code audit_logs} — belt and
 * braces, not redundant: the DB grant is what makes it a real guarantee, this is what keeps the
 * application code itself honest too) "and read-only at the grant level", "target the read
 * replica". Raw {@code JdbcTemplate}, not {@code AuditLogRepository} (which is JPA against the
 * primary, owned by {@code common/audit/} for writes) — a second read path against the same table
 * via a different pool, same reasoning {@code EntitlementFlagsLoader}/{@code OwnershipGuard}
 * already establish for native reads in {@code common/}.
 */
@Service
public class AuditQueryService {

    private final JdbcTemplate replicaJdbcTemplate;

    public AuditQueryService(@Qualifier("replicaJdbcTemplate") JdbcTemplate replicaJdbcTemplate) {
        this.replicaJdbcTemplate = replicaJdbcTemplate;
    }

    public PageResponse<AuditLogResponse> list(String entityType, String userUuid, LocalDate from, Pageable pageable) {
        Timestamp fromTimestamp = from == null ? null : Timestamp.from(from.atStartOfDay(ZoneOffset.UTC).toInstant());

        long total = countRow(entityType, userUuid, fromTimestamp);
        if (total == 0) {
            return PageResponse.from(new PageImpl<>(List.of(), pageable, 0));
        }

        List<AuditLogResponse> rows = replicaJdbcTemplate.query("""
                SELECT a.id, actor.uuid AS user_uuid, a.action, a.entity_type,
                       CASE WHEN a.entity_type = 'User' THEN NULL ELSE a.entity_id END AS entity_id,
                       CASE WHEN a.entity_type = 'User' THEN target.uuid ELSE NULL END AS entity_uuid,
                       a.old_value, a.new_value, a.ip_address, a.created_at
                  FROM audit_logs a
                  LEFT JOIN users actor ON actor.id = a.user_id
                  LEFT JOIN users target ON a.entity_type = 'User' AND target.id = a.entity_id
                 WHERE (? IS NULL OR a.entity_type = ?)
                   AND (? IS NULL OR actor.uuid = ?)
                   AND (? IS NULL OR a.created_at >= ?)
                 ORDER BY a.created_at DESC
                 LIMIT ? OFFSET ?
                """,
                new Object[] {
                        entityType, entityType, userUuid, userUuid, fromTimestamp, fromTimestamp,
                        pageable.getPageSize(), pageable.getOffset()
                },
                new int[] {
                        Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP, Types.TIMESTAMP,
                        Types.INTEGER, Types.BIGINT
                },
                rowMapper());

        return PageResponse.from(new PageImpl<>(rows, pageable, total));
    }

    private long countRow(String entityType, String userUuid, Timestamp fromTimestamp) {
        Long count = replicaJdbcTemplate.query("""
                SELECT COUNT(*) AS total
                  FROM audit_logs a
                  LEFT JOIN users actor ON actor.id = a.user_id
                 WHERE (? IS NULL OR a.entity_type = ?)
                   AND (? IS NULL OR actor.uuid = ?)
                   AND (? IS NULL OR a.created_at >= ?)
                """,
                new Object[] { entityType, entityType, userUuid, userUuid, fromTimestamp, fromTimestamp },
                new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP, Types.TIMESTAMP },
                (rs, rowNum) -> rs.getLong("total"))
                .stream().findFirst().orElse(0L);
        return count == null ? 0 : count;
    }

    private RowMapper<AuditLogResponse> rowMapper() {
        return (rs, rowNum) -> new AuditLogResponse(
                rs.getLong("id"),
                rs.getString("user_uuid"),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getObject("entity_id") == null ? null : rs.getLong("entity_id"),
                rs.getString("entity_uuid"),
                rs.getString("old_value"),
                rs.getString("new_value"),
                rs.getString("ip_address"),
                toInstant(rs.getTimestamp("created_at")));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
