package com.moriah.skillhub.site;

import com.moriah.skillhub.site.dto.PublicStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only aggregate counts for the public landing page. Plain {@code JdbcTemplate} COUNTs
 * rather than six repository methods spread across as many modules — this is presentational
 * marketing data, not a domain operation, and every table it touches
 * ({@code batch_students}, {@code batches}, {@code placements}, {@code certificates}) is a
 * cross-module read that no single feature owns.
 */
@Service
@RequiredArgsConstructor
public class PublicStatsService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public PublicStatsResponse snapshot() {
        return new PublicStatsResponse(
                count("SELECT COUNT(*) FROM batch_students WHERE status = 'GRADUATED'"),
                count("SELECT COUNT(DISTINCT user_id) FROM batch_students WHERE status IN ('ACTIVE', 'ON_PIP')"),
                count("SELECT COUNT(*) FROM batches WHERE status = 'ACTIVE'"),
                count("SELECT COUNT(*) FROM placements WHERE stage = 'PLACED'"),
                count("SELECT COUNT(*) FROM certificates WHERE revoked_at IS NULL"),
                count("SELECT COUNT(DISTINCT client_id) FROM placements"));
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
