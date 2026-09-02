package com.moriah.skillhub.common.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Audit 2026-08-31 (H1): {@code @EnableAsync} alone left every {@code @Async} method on Boot's
 * auto-configured {@code SimpleAsyncTaskExecutor}, which with {@code spring.threads.virtual.enabled}
 * has an <i>unbounded</i> concurrency limit — it starts a fresh virtual thread per task forever,
 * with no queue and no rejection. The two real {@code @Async} workloads ({@code
 * InvoiceGenerationJob} PDF+S3 on every payment capture, {@code ExportGenerationService} full
 * replica read + XLSX) each hold DB connections while running, so a webhook burst or a handful of
 * concurrent exports could drain the connection pool and stall unrelated request threads with no
 * backpressure signal.
 * <p>
 * Now: a bounded {@link ThreadPoolTaskExecutor} is the default {@code @Async} executor, with a
 * {@code CallerRunsPolicy} so a saturated queue pushes work back onto the submitter (backpressure)
 * instead of growing without limit. Exports get their own tiny executor ({@code exportExecutor})
 * so a slow export can never starve invoice generation, and vice versa. Platform threads are the
 * right choice here — these tasks are CPU- and IO-heavy batch work, not the many-small-blocking-
 * calls shape virtual threads exist for.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        return applicationTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }

    /** Default executor for {@code @Async} methods with no explicit qualifier (e.g. {@code
     * InvoiceGenerationJob}). */
    @Bean("applicationTaskExecutor")
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** Dedicated small pool for {@code ExportGenerationService.generate} — bounded hard so
     * several admins triggering exports at once cannot consume the whole {@link
     * #applicationTaskExecutor}. */
    @Bean("exportExecutor")
    public ThreadPoolTaskExecutor exportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("export-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
