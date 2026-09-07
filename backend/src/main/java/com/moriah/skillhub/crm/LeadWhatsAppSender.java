package com.moriah.skillhub.crm;

import com.moriah.skillhub.common.notification.dispatch.WhatsAppDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * A separate bean, deliberately — same reasoning {@code NotificationWriter}/{@code
 * NotificationService} document for splitting the {@code TransactionSynchronizationManager} call
 * out of the service that decides *whether* to send: {@link LeadService}'s own unit tests run
 * with plain Mockito, no live Spring transaction, so {@code
 * TransactionSynchronizationManager.registerSynchronization} would throw {@code
 * IllegalStateException: Transaction synchronization is not active} if called inline there. As a
 * separate, mockable collaborator, {@code LeadService}'s tests verify the call happened without
 * ever touching real transaction synchronization.
 * <p>
 * build-plan.md: "WhatsApp outbound uses pre-approved template codes outside the 24h session
 * window" — {@code templateCode} is that pre-approved name (never free text, enforced by {@code
 * AddLeadActivityRequest}'s compact constructor). Deferred to afterCommit per code-standards.md's
 * Transactions rule ("never make an outbound HTTP call inside a transaction") — same shape as
 * {@code NotificationService.enqueueAfterCommit}, just calling {@link
 * WhatsAppDispatcher#sendTemplate} directly instead of going through the {@code notifications}
 * table, since a {@code Lead} has no {@code users.id} to own that row.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class LeadWhatsAppSender {

    private final WhatsAppDispatcher whatsAppDispatcher;

    void sendAfterCommit(String templateCode, String phone) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    whatsAppDispatcher.sendTemplate(templateCode, phone, List.of());
                } catch (Exception e) {
                    log.warn("[crm/whatsapp] outbound template {} to a lead failed", templateCode, e);
                }
            }
        });
    }
}
