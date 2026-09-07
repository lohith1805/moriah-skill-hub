package com.moriah.skillhub.crm;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.crm.dto.CreateLeadCampaignRequest;
import com.moriah.skillhub.crm.dto.LeadCampaignResponse;
import com.moriah.skillhub.crm.dto.UpdateLeadCampaignRequest;
import com.moriah.skillhub.crm.entity.LeadCampaign;
import com.moriah.skillhub.crm.entity.LeadCampaignChannel;
import com.moriah.skillhub.crm.entity.LeadCampaignStatus;
import com.moriah.skillhub.crm.repository.LeadCampaignRecipientRepository;
import com.moriah.skillhub.crm.repository.LeadCampaignRepository;
import com.moriah.skillhub.crm.repository.LeadRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadCampaignServiceTest {

    @Mock
    private LeadCampaignRepository campaignRepository;
    @Mock
    private LeadCampaignRecipientRepository recipientRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private LeadCampaignService service;

    private CreateLeadCampaignRequest createRequest(LocalDate start, LocalDate end) {
        return new CreateLeadCampaignRequest("Autumn Webinar Push", LeadCampaignChannel.WEBINAR,
                "  ", start, end, new BigDecimal("50000"), 200, null);
    }

    private LeadCampaign campaign(long id) {
        LeadCampaign c = new LeadCampaign();
        c.setId(id);
        c.setName("Autumn Webinar Push");
        c.setChannel(LeadCampaignChannel.WEBINAR);
        c.setStartDate(LocalDate.of(2026, 9, 1));
        c.setStatus(LeadCampaignStatus.PLANNED);
        c.setCreatedBy(4L);
        return c;
    }

    @Test
    void create_startsPlannedAndTrimsBlankDescription() {
        when(campaignRepository.save(any(LeadCampaign.class))).thenAnswer(inv -> {
            LeadCampaign c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        User creator = new User();
        creator.setId(4L);
        creator.setUuid("agent-uuid");
        when(userRepository.findAllById(List.of(4L))).thenReturn(List.of(creator));

        LeadCampaignResponse response = service.create(
                createRequest(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)), 4L);

        ArgumentCaptor<LeadCampaign> captor = ArgumentCaptor.forClass(LeadCampaign.class);
        verify(campaignRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(LeadCampaignStatus.PLANNED);
        assertThat(captor.getValue().getDescription()).isNull();
        assertThat(response.createdByUuid()).isEqualTo("agent-uuid");
        verify(auditLogService).record(eq(4L), eq("LEAD_CAMPAIGN_CREATED"), any(), any(), any(), any());
    }

    @Test
    void create_endBeforeStart_throwsBusinessRule() {
        assertThatThrownBy(() -> service.create(
                createRequest(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 1)), 4L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void update_unknownId_throwsNotFound() {
        when(campaignRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, new UpdateLeadCampaignRequest(
                "x", LeadCampaignChannel.EMAIL, null, LocalDate.of(2026, 1, 1), null, null, null,
                LeadCampaignStatus.ACTIVE, null), 4L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEAD_CAMPAIGN_NOT_FOUND);
    }

    @Test
    void update_replacesFieldsIncludingStatus() {
        LeadCampaign c = campaign(3L);
        when(campaignRepository.findById(3L)).thenReturn(Optional.of(c));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        LeadCampaignResponse response = service.update(3L, new UpdateLeadCampaignRequest(
                "Renamed", LeadCampaignChannel.PAID_ADS, "desc", LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 12, 5), new BigDecimal("99999"), 500, LeadCampaignStatus.ACTIVE, null), 4L);

        assertThat(c.getName()).isEqualTo("Renamed");
        assertThat(c.getChannel()).isEqualTo(LeadCampaignChannel.PAID_ADS);
        assertThat(c.getStatus()).isEqualTo(LeadCampaignStatus.ACTIVE);
        assertThat(response.targetLeads()).isEqualTo(500);
    }

    @Test
    void create_withLeadIds_savesRecipients() {
        when(campaignRepository.save(any(LeadCampaign.class))).thenAnswer(inv -> {
            LeadCampaign c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(userRepository.findAllById(any())).thenReturn(List.of());
        com.moriah.skillhub.crm.entity.Lead lead1 = new com.moriah.skillhub.crm.entity.Lead();
        lead1.setId(10L);
        com.moriah.skillhub.crm.entity.Lead lead2 = new com.moriah.skillhub.crm.entity.Lead();
        lead2.setId(11L);
        when(leadRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(lead1, lead2));

        CreateLeadCampaignRequest request = new CreateLeadCampaignRequest("Autumn Webinar Push",
                LeadCampaignChannel.WEBINAR, null, LocalDate.of(2026, 9, 1), null,
                null, null, List.of(10L, 11L));

        LeadCampaignResponse response = service.create(request, 4L);

        assertThat(response.leadIds()).containsExactlyInAnyOrder(10L, 11L);
        verify(recipientRepository).saveAll(any());
    }

    @Test
    void create_withUnknownLeadId_throwsNotFound() {
        when(leadRepository.findAllById(List.of(999L))).thenReturn(List.of());

        CreateLeadCampaignRequest request = new CreateLeadCampaignRequest("Autumn Webinar Push",
                LeadCampaignChannel.WEBINAR, null, LocalDate.of(2026, 9, 1), null,
                null, null, List.of(999L));

        assertThatThrownBy(() -> service.create(request, 4L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEAD_NOT_FOUND);
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void update_withLeadIds_replacesRecipientsWholesale() {
        LeadCampaign c = campaign(3L);
        when(campaignRepository.findById(3L)).thenReturn(Optional.of(c));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        com.moriah.skillhub.crm.entity.Lead lead = new com.moriah.skillhub.crm.entity.Lead();
        lead.setId(20L);
        when(leadRepository.findAllById(List.of(20L))).thenReturn(List.of(lead));

        LeadCampaignResponse response = service.update(3L, new UpdateLeadCampaignRequest(
                "Renamed", LeadCampaignChannel.PAID_ADS, "desc", LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 12, 5), new BigDecimal("99999"), 500, LeadCampaignStatus.ACTIVE,
                List.of(20L)), 4L);

        assertThat(response.leadIds()).containsExactly(20L);
        verify(recipientRepository).deleteByIdCampaignId(3L);
        verify(recipientRepository).saveAll(any());
    }

    @Test
    void cancel_movesToCancelled_onlyOnce() {
        LeadCampaign c = campaign(3L);
        when(campaignRepository.findById(3L)).thenReturn(Optional.of(c));

        service.cancel(3L, 4L);
        assertThat(c.getStatus()).isEqualTo(LeadCampaignStatus.CANCELLED);
        verify(auditLogService).record(eq(4L), eq("LEAD_CAMPAIGN_CANCELLED"), any(), any(), any(), any());

        service.cancel(3L, 4L); // idempotent — no second audit
        verify(auditLogService).record(eq(4L), eq("LEAD_CAMPAIGN_CANCELLED"), any(), any(), any(), any());
    }

    @Test
    void list_passesStatusFilterThrough() {
        when(campaignRepository.search(eq(LeadCampaignStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(campaign(1L)), PageRequest.of(0, 20), 1));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        PageResponse<LeadCampaignResponse> page = service.list(LeadCampaignStatus.ACTIVE, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        verify(campaignRepository).search(eq(LeadCampaignStatus.ACTIVE), any(Pageable.class));
    }
}
