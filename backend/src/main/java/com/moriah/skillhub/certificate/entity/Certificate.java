package com.moriah.skillhub.certificate.entity;

import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * {@code user}/{@code batch}/{@code issuedBy} are real {@code @ManyToOne} associations — {@code
 * User} and {@code Batch} are the established shared-kernel exception (code-standards.md's
 * canonical {@code SprintService}/{@code Sprint} example), and a second {@code User} FK on the
 * same entity already has precedent ({@code PipRecord.reviewedBy} alongside {@code
 * PipRecord.user}, {@code Attendance.markedBy} alongside {@code Attendance.user}).
 * <p>
 * {@code certificateNumber} starts {@code null} — {@code CertificateService#issue} persists this
 * entity once to obtain its {@code IDENTITY}-generated {@code id}, then computes and sets the
 * number from that id and saves again in the same transaction (Constants#CERTIFICATE_PREFIX's own
 * Javadoc explains why this can't be computed up front, mirroring {@code Invoice.invoiceNumber}'s
 * identical id-dependent scheme).
 */
@Entity
@Table(name = "certificates")
@Getter
@Setter
@NoArgsConstructor
public class Certificate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(name = "certificate_number", length = 30)
    private String certificateNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_type", nullable = false, length = 20)
    private CertificateType certificateType = CertificateType.COMPLETION;

    // CHAR(36) columnDefinition isn't needed here (unlike User.uuid/RefreshToken.tokenHash) — V14
    // declares this CHAR(12) and a 12-character generated code fits without truncation either way,
    // but Hibernate's default VARCHAR mapping for a plain String column would still mismatch
    // ddl-auto: validate's exact-type check against a CHAR column, so the override is required.
    @Column(name = "verification_code", nullable = false, columnDefinition = "CHAR(12)")
    private String verificationCode;

    @Column(name = "pdf_key", length = 255)
    private String pdfKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issued_by")
    private User issuedBy;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 500)
    private String revokeReason;
}
