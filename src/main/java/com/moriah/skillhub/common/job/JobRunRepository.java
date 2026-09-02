package com.moriah.skillhub.common.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {

    /** Most recent run of a given job, by start time — used by {@link JobChainGuard} to enforce
     * the nightly attendance → metrics → PIP ordering (audit 2026-08-31, H4). */
    Optional<JobRun> findFirstByJobNameOrderByStartedAtDesc(String jobName);
}
