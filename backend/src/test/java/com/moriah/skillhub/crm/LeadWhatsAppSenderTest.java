package com.moriah.skillhub.crm;

import com.moriah.skillhub.common.notification.dispatch.WhatsAppDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LeadWhatsAppSenderTest {

    @Mock
    private WhatsAppDispatcher whatsAppDispatcher;

    // Built per-test, not as a field initializer — a field initializer runs during construction,
    // before MockitoExtension's beforeEach injects @Mock fields, so it would capture a null
    // whatsAppDispatcher into the anonymous TransactionSynchronization LeadWhatsAppSender builds.
    private LeadWhatsAppSender sender() {
        return new LeadWhatsAppSender(whatsAppDispatcher);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendAfterCommit_firesOnlyOnceTheTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();

        sender().sendAfterCommit("demo_followup_v1", "15550001111");
        verify(whatsAppDispatcher, never()).sendTemplate(anyString(), anyString(), anyList());

        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());

        verify(whatsAppDispatcher).sendTemplate(eq("demo_followup_v1"), eq("15550001111"), eq(List.of()));
    }

    @Test
    void sendAfterCommit_dispatchFailureIsSwallowedNotPropagated() {
        TransactionSynchronizationManager.initSynchronization();
        doThrow(new RuntimeException("gateway down"))
                .when(whatsAppDispatcher).sendTemplate(anyString(), anyString(), anyList());

        sender().sendAfterCommit("demo_followup_v1", "15550001111");

        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
        // No exception propagated out of the loop above is itself the assertion.
    }
}
