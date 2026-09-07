package com.moriah.skillhub.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables {@code @CreatedDate}/{@code @LastModifiedDate} on {@code BaseEntity}. No auditor-aware
 * bean yet — {@code createdBy}/{@code updatedBy} auditing arrives with {@code AuditableEntity}
 * once a "current user" concept exists (feature 03).
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
