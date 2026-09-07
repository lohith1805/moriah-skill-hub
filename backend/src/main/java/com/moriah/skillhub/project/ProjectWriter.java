package com.moriah.skillhub.project;

import com.moriah.skillhub.project.entity.Project;
import com.moriah.skillhub.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A separate bean, {@code REQUIRES_NEW}, deliberately — same reasoning {@code QuizAttemptWriter}/
 * {@code TaskSubmissionWriter} document: catching a duplicate-key failure from a Hibernate-backed
 * {@code save()} and continuing still leaves the *session* poisoned the instant a flush fails,
 * independent of whether application code catches the translated exception afterward. {@code
 * projects.slug} has the same unique-constraint race shape a concurrent double-create can hit —
 * two requests generating the same base slug from the same (or a colliding) title — except the
 * recovery here is "retry with a new candidate slug," not "read back the winner's row," since a
 * slug collision has no natural "same logical resource" to recover.
 */
@Service
@RequiredArgsConstructor
class ProjectWriter {

    private final ProjectRepository projectRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Project trySave(Project project) {
        return projectRepository.saveAndFlush(project);
    }
}
