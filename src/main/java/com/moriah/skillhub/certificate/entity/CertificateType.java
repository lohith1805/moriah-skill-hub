package com.moriah.skillhub.certificate.entity;

/** Matches V14's {@code chk_certificates_type} CHECK constraint exactly (build-plan.md feature 20
 * doesn't name a value set explicitly beyond "certificate_type" existing as a column — this
 * build's own closed set, the same "no enumerated value set in architecture.md" treatment
 * {@code EmploymentType}/{@code LeaveType} already document). Defaults to {@code COMPLETION} when
 * an issuer's request omits it (see {@code IssueCertificateRequest}). */
public enum CertificateType {
    COMPLETION,
    EXCELLENCE
}
