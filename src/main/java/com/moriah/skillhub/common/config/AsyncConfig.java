package com.moriah.skillhub.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async} methods (e.g. {@code InvoiceGenerationJob}) run on Boot's auto-configured
 * virtual-thread executor — {@code spring.threads.virtual.enabled: true} is already set
 * project-wide (application.yml), so no custom {@code ThreadPoolTaskExecutor} bean is needed
 * here; {@code @EnableAsync} alone is enough to make Spring pick it up.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
