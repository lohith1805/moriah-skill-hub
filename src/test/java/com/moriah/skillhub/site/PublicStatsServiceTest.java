package com.moriah.skillhub.site;

import com.moriah.skillhub.site.dto.PublicStatsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicStatsServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private PublicStatsService service;

    @Test
    void snapshot_mapsEachCountIntoTheResponseInOrder() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(42L, 130L, 5L, 27L, 60L, 9L);

        PublicStatsResponse stats = service.snapshot();

        assertThat(stats.graduates()).isEqualTo(42);
        assertThat(stats.activeLearners()).isEqualTo(130);
        assertThat(stats.activeBatches()).isEqualTo(5);
        assertThat(stats.placements()).isEqualTo(27);
        assertThat(stats.certificatesIssued()).isEqualTo(60);
        assertThat(stats.hiringPartners()).isEqualTo(9);
    }

    @Test
    void snapshot_nullCountBecomesZero() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

        PublicStatsResponse stats = service.snapshot();

        assertThat(stats.graduates()).isZero();
        assertThat(stats.hiringPartners()).isZero();
    }
}
