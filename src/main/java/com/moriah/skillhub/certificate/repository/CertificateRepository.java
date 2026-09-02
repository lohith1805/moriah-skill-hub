package com.moriah.skillhub.certificate.repository;

import com.moriah.skillhub.certificate.entity.Certificate;
import com.moriah.skillhub.certificate.entity.CertificateType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    /** {@code CertificateService#generateUniqueVerificationCode}'s collision probe — a single
     * indexed lookup against {@code uq_certificates_verification_code}, not a table scan. */
    boolean existsByVerificationCode(String verificationCode);

    /** Audit 2026-08-31 (M17): guard against issuing a second live certificate for the same
     * (student, batch, type). A revoked one does not count — re-issuing a corrected certificate
     * after a revoke is a legitimate flow. */
    boolean existsByUserIdAndBatchIdAndCertificateTypeAndRevokedAtIsNull(
            Long userId, Long batchId, CertificateType certificateType);

    /** {@code GET /api/v1/certificates/verify/{code}} — public, unauthenticated, and the <b>only</b>
     * column this lookup is ever keyed on (build-plan.md: "never accept a certificate id here").
     * {@code @EntityGraph} on {@code user}/{@code batch} — {@code CertificateService#verify} reads
     * both associations to build the public response; without it that's two extra lazy-load
     * round trips per call on this feature's hottest path (code-standards.md "N+1 Prevention"). */
    @EntityGraph(attributePaths = {"user", "batch"})
    Optional<Certificate> findByVerificationCode(String verificationCode);

    /** {@code GET /api/v1/certificates/me} — the caller already knows their own uuid/fullName
     * (threaded in via {@code @CurrentUserUuid}), so only {@code batch} needs eager loading here;
     * {@code @EntityGraph}, not {@code JOIN FETCH}, because this is paginated (same reasoning as
     * {@code BatchRepository.findAll}). */
    @EntityGraph(attributePaths = "batch")
    Page<Certificate> findByUserId(Long userId, Pageable pageable);

    /** The Open Stub cleared this feature — {@code UserService#getPortfolio}'s {@code
     * issuedCertificates}: "never show a revoked one as an achievement." No {@code @EntityGraph}
     * needed — {@code CertificateService#issuedCertificatesFor} reads only {@code
     * certificateType}/{@code issuedAt}/{@code verificationCode}, none of which touch a lazy
     * association. Deliberately unpaginated (a portfolio's own certificate list is inherently
     * small, per this feature's own build brief) — the one AGENTS.md "every list is paginated"
     * exception this project already established for {@code ProjectService}'s per-project asset
     * lists. */
    List<Certificate> findByUserIdAndRevokedAtIsNullOrderByIssuedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "batch", "issuedBy"})
    @Query("SELECT c FROM Certificate c WHERE c.id = :id")
    Optional<Certificate> findWithAssociationsById(@Param("id") Long id);
}
