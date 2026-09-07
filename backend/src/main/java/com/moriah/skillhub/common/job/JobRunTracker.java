package com.moriah.skillhub.common.job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * {@code REQUIRES_NEW} throughout — same reasoning as {@code AuditLogService}: a job_runs row
 * must survive a rollback of the job's own work, or the one case this table exists for (a job
 * that failed) is exactly the case that leaves no trace.
 */
@Service
@RequiredArgsConstructor
public class JobRunTracker {

    private final JobRunRepository jobRunRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobRun start(String jobName) {
        JobRun run = new JobRun();
        run.setJobName(jobName);
        run.setStartedAt(Instant.now());
        run.setStatus(JobRunStatus.RUNNING);
        return jobRunRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Long jobRunId, int itemsProcessed) {
        jobRunRepository.findById(jobRunId).ifPresent(run -> {
            run.setStatus(JobRunStatus.SUCCESS);
            run.setItemsProcessed(itemsProcessed);
            run.setCompletedAt(Instant.now());
            jobRunRepository.save(run);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long jobRunId, String errorMessage) {
        jobRunRepository.findById(jobRunId).ifPresent(run -> {
            run.setStatus(JobRunStatus.FAILED);
            run.setErrorMessage(errorMessage);
            run.setCompletedAt(Instant.now());
            jobRunRepository.save(run);
        });
    }
}
