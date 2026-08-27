package com.moriah.skillhub.crm.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.crm.entity.Lead;
import com.moriah.skillhub.crm.entity.LeadActivity;
import com.moriah.skillhub.crm.entity.LeadActivityType;
import com.moriah.skillhub.crm.repository.LeadActivityRepository;
import com.moriah.skillhub.crm.repository.LeadRepository;
import com.moriah.skillhub.payment.WebhookIdempotencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookServiceTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadActivityRepository leadActivityRepository;
    @Mock
    private WebhookIdempotencyService webhookIdempotencyService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WhatsAppWebhookService service() {
        return new WhatsAppWebhookService(leadRepository, leadActivityRepository, webhookIdempotencyService);
    }

    private JsonNode inboundMessagePayload(String messageId, String from, String text) throws Exception {
        String json = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "id": "entry-1",
                    "changes": [{
                      "value": {
                        "messaging_product": "whatsapp",
                        "messages": [{
                          "from": "%s",
                          "id": "%s",
                          "timestamp": "1700000000",
                          "type": "text",
                          "text": {"body": "%s"}
                        }]
                      },
                      "field": "messages"
                    }]
                  }]
                }
                """.formatted(from, messageId, text);
        return objectMapper.readTree(json);
    }

    @Test
    void handleInbound_matchingLead_logsActivity() throws Exception {
        Lead lead = new Lead();
        lead.setId(3L);
        when(webhookIdempotencyService.claim(eq("WHATSAPP"), eq("wamid.1"), any(), any())).thenReturn(true);
        when(leadRepository.findFirstByPhoneOrderByCreatedAtDesc("15551234567")).thenReturn(Optional.of(lead));

        service().handleInbound(inboundMessagePayload("wamid.1", "15551234567", "Interested, please call"), "{}");

        ArgumentCaptor<LeadActivity> captor = ArgumentCaptor.forClass(LeadActivity.class);
        verify(leadActivityRepository).save(captor.capture());
        assertThat(captor.getValue().getActivityType()).isEqualTo(LeadActivityType.WHATSAPP_INBOUND);
        assertThat(captor.getValue().getNotes()).isEqualTo("Interested, please call");
        assertThat(captor.getValue().getLead()).isSameAs(lead);
    }

    @Test
    void handleInbound_noMatchingLead_logsNothing() throws Exception {
        when(webhookIdempotencyService.claim(eq("WHATSAPP"), eq("wamid.2"), any(), any())).thenReturn(true);
        when(leadRepository.findFirstByPhoneOrderByCreatedAtDesc("15559999999")).thenReturn(Optional.empty());

        service().handleInbound(inboundMessagePayload("wamid.2", "15559999999", "hello"), "{}");

        verify(leadActivityRepository, never()).save(any());
    }

    @Test
    void handleInbound_duplicateMessageId_skipsProcessing() throws Exception {
        when(webhookIdempotencyService.claim(eq("WHATSAPP"), eq("wamid.3"), any(), any())).thenReturn(false);

        service().handleInbound(inboundMessagePayload("wamid.3", "15551234567", "hello again"), "{}");

        verify(leadRepository, never()).findFirstByPhoneOrderByCreatedAtDesc(any());
        verify(leadActivityRepository, never()).save(any());
    }
}
