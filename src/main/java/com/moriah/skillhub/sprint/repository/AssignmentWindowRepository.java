package com.moriah.skillhub.sprint.repository;

import com.moriah.skillhub.sprint.entity.AssignmentWindow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface AssignmentWindowRepository extends JpaRepository<AssignmentWindow, Long> {

    boolean existsByBatchIdAndWeekStart(Long batchId, LocalDate weekStart);

    Page<AssignmentWindow> findByBatchIdOrderByWeekStartAsc(Long batchId, Pageable pageable);
}
