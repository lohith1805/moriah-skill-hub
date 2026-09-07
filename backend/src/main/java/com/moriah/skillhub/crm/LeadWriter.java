package com.moriah.skillhub.crm;

import com.moriah.skillhub.crm.entity.Lead;
import com.moriah.skillhub.crm.repository.LeadRepository;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * A separate bean, {@code REQUIRES_NEW}, same reasoning as {@code ProjectWriter}: catching {@code
 * uq_leads_dedupe_hash}'s violation from a Hibernate-backed {@code save()} and continuing still
 * leaves the caller's own session poisoned. Two concurrent landing-page submissions for the same
 * email+phone race here; the loser's {@link #tryCreate} returns empty and {@link LeadService}
 * falls back to the update-existing path — "a duplicate updates the existing lead... never a
 * second row" (build-plan.md), including under a genuine race, not just a sequential recheck.
 */
@Service
@RequiredArgsConstructor
class LeadWriter {

    private final LeadRepository leadRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<Lead> tryCreate(Lead lead) {
        try {
            return Optional.of(leadRepository.saveAndFlush(lead));
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
    }

    /** Also {@code REQUIRES_NEW} — MySQL/InnoDB's REPEATABLE READ snapshot is fixed at the outer
     * transaction's first read (the empty {@code findByDedupeHash} in {@code LeadService.create}),
     * so re-running that same query in the same transaction after {@link #tryCreate} loses a race
     * still can't see the concurrent winner's just-committed row. A fresh transaction gets a fresh
     * snapshot taken after that commit (same fix as {@code QuizAttemptWriter.findExisting}). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<Lead> findByDedupeHash(String dedupeHash) {
        return leadRepository.findByDedupeHash(dedupeHash);
    }
}
