package com.moriah.skillhub.batch;

/**
 * Published by {@link BatchService#create} after the new {@code batches} row commits. Consumed by
 * {@link PendingAllocationDrainer} to place any students who paid for this track while no batch
 * existed. An in-process Spring event (not feature 08's Redis queue) — same pattern as
 * {@code PaymentCapturedEvent} — chosen only to break the {@code BatchService} →
 * {@code BatchAllocationService} → {@code UserService} → … → {@code BatchService} bean cycle a
 * direct call would create.
 */
public record BatchCreatedEvent(String trackCode) {
}
