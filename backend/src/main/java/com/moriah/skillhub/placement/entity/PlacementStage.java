package com.moriah.skillhub.placement.entity;

/**
 * The client-placement pipeline, in order. {@code PlacementService} rejects any move to a lower
 * ordinal; {@link #REJECTED} is the one exception — reachable from any non-terminal stage.
 *
 * <p>Ownership per <em>target</em> stage (enforced in the service):
 * <ul>
 *   <li>the requesting CLIENT drives the technical rounds and {@link #CLIENT_SIGNED};</li>
 *   <li>HR_MANAGER / ADMIN drive the HR round, document verification, offer creation and the
 *       final {@link #PLACED};</li>
 *   <li>the candidate STUDENT sets {@link #STUDENT_SIGNED};</li>
 *   <li>{@link #REJECTED} may be set by the CLIENT or HR/ADMIN.</li>
 * </ul>
 */
public enum PlacementStage {
    SHORTLISTED,
    TECHNICAL_SCHEDULED,
    TECHNICAL_COMPLETED,
    TECHNICAL_APPROVED,
    HR_SCHEDULED,
    HR_COMPLETED,
    HR_APPROVED,
    DOCUMENT_VERIFICATION,
    OFFER_CREATED,
    CLIENT_SIGNED,
    STUDENT_SIGNED,
    PLACED,
    REJECTED;

    public boolean isTerminal() {
        return this == PLACED || this == REJECTED;
    }
}
