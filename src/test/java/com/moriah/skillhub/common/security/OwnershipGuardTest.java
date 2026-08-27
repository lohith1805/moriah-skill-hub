package com.moriah.skillhub.common.security;

import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnershipGuardTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private OwnershipGuard guard;

    @Test
    void requireOwner_matchingIds_doesNotThrow() {
        guard.requireOwner(42L, 42L);
    }

    @Test
    void requireOwner_differentIds_throwsForbidden() {
        assertThatThrownBy(() -> guard.requireOwner(1L, 2L))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void canAccessKey_resumeOwnedByCaller_returnsTrue() {
        assertThat(guard.canAccessKey("uuid-a", "resumes/uuid-a/resume.pdf")).isTrue();
    }

    @Test
    void canAccessKey_resumeOwnedByAnotherUser_returnsFalse() {
        assertThat(guard.canAccessKey("uuid-a", "resumes/uuid-b/resume.pdf")).isFalse();
    }

    @Test
    void canAccessKey_hrDocumentOwnedByCaller_returnsTrue() {
        assertThat(guard.canAccessKey("uuid-a", "hr-documents/uuid-a/id-proof-abc.pdf")).isTrue();
    }

    @Test
    void canAccessKey_unrecognizedNamespace_deniesByDefault() {
        // certificates/, invoices/, etc. aren't resource-id-owned yet (those entities don't
        // exist until later features) — must deny, never guess.
        assertThat(guard.canAccessKey("uuid-a", "certificates/MSH-CERT-2026-001.pdf")).isFalse();
    }

    @Test
    void canAccessKey_publishedProjectAsset_anyCallerReturnsTrue() {
        stubProjectAccessible(true);

        assertThat(guard.canAccessKey("uuid-bystander", "projects/7/assets/3-screenshot.png")).isTrue();
    }

    @Test
    void canAccessKey_draftProjectAssetOwnedByCreator_returnsTrue() {
        stubProjectAccessible(true);

        assertThat(guard.canAccessKey("uuid-creator", "projects/7/assets/3-screenshot.png")).isTrue();
    }

    @Test
    void canAccessKey_draftProjectAssetNotOwnedByCaller_returnsFalse() {
        stubProjectAccessible(false);

        assertThat(guard.canAccessKey("uuid-bystander", "projects/7/assets/3-screenshot.png")).isFalse();
    }

    @Test
    void canAccessKey_projectKeyWithMalformedId_deniesWithoutQuerying() {
        assertThat(guard.canAccessKey("uuid-a", "projects/not-a-number/assets/3-screenshot.png")).isFalse();
    }

    @Test
    void requireKeyAccess_notOwned_throwsForbidden() {
        assertThatThrownBy(() -> guard.requireKeyAccess("uuid-a", "resumes/uuid-b/resume.pdf"))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @SuppressWarnings("unchecked")
    private void stubProjectAccessible(boolean accessible) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString(), anyString(), anyLong()))
                .thenReturn(List.of(accessible));
    }
}
