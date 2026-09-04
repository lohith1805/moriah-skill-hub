package com.moriah.skillhub.project.repository;

import com.moriah.skillhub.project.entity.ChallengeSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeSubmissionRepository extends JpaRepository<ChallengeSubmission, Long> {

    /** The calling student's own submissions, newest first. {@code JOIN FETCH} the challenge and
     * its project so the list row can show which challenge/project each belongs to without N+1. */
    @Query("""
            SELECT s FROM ChallengeSubmission s
              JOIN FETCH s.challenge c
              JOIN FETCH c.project p
             WHERE s.student.id = :studentId
             ORDER BY s.id DESC
            """)
    Page<ChallengeSubmission> findMine(@Param("studentId") Long studentId, Pageable pageable);

    /** Every submission for one challenge (developer / trainer review view), newest first. */
    @Query("""
            SELECT s FROM ChallengeSubmission s
              JOIN FETCH s.challenge c
              JOIN FETCH c.project p
              JOIN FETCH s.student u
             WHERE c.id = :challengeId
             ORDER BY s.id DESC
            """)
    Page<ChallengeSubmission> findForChallenge(@Param("challengeId") Long challengeId, Pageable pageable);
}
