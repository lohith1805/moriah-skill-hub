package com.moriah.skillhub.crm;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.crm.dto.AddLeadActivityRequest;
import com.moriah.skillhub.crm.dto.CreateLeadRequest;
import com.moriah.skillhub.crm.dto.LeadActivityResponse;
import com.moriah.skillhub.crm.dto.LeadResponse;
import com.moriah.skillhub.crm.dto.SalesTargetResponse;
import com.moriah.skillhub.crm.dto.UpdateLeadStatusRequest;
import com.moriah.skillhub.crm.entity.Lead;
import com.moriah.skillhub.crm.entity.LeadActivity;
import com.moriah.skillhub.crm.entity.LeadActivityType;
import com.moriah.skillhub.crm.entity.LeadSource;
import com.moriah.skillhub.crm.entity.LeadStatus;
import com.moriah.skillhub.crm.entity.SalesTarget;
import com.moriah.skillhub.crm.repository.LeadActivityRepository;
import com.moriah.skillhub.crm.repository.LeadRepository;
import com.moriah.skillhub.crm.repository.SalesTargetRepository;
import com.moriah.skillhub.subscription.repository.SubscriptionPlanRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadActivityRepository leadActivityRepository;
    @Mock
    private SalesTargetRepository salesTargetRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private LeadWriter leadWriter;
    @Mock
    private LeadWhatsAppSender leadWhatsAppSender;

    private LeadService service() {
        return new LeadService(leadRepository, leadActivityRepository, salesTargetRepository,
                userRepository, subscriptionPlanRepository, leadWriter, leadWhatsAppSender);
    }

    private CreateLeadRequest createRequest() {
        return new CreateLeadRequest("Ada Lovelace", "ada@example.com", "+1 555 000 1111",
                LeadSource.LANDING_PAGE, "INDIVIDUAL", null, null);
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        user.setFullName("Agent " + id);
        return user;
    }

    // --- create ---

    @Test
    void create_newLead_savesAndLogsOneCreationActivity() {
        when(leadRepository.findByDedupeHash(any())).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L));
        when(leadWriter.tryCreate(any())).thenAnswer(inv -> {
            Lead lead = inv.getArgument(0);
            lead.setId(1L);
            return Optional.of(lead);
        });

        LeadResponse response = service().create(createRequest(), 9L);

        assertThat(response.status()).isEqualTo(LeadStatus.NEW);
        assertThat(response.name()).isEqualTo("Ada Lovelace");
        assertThat(response.assignedAgentUuid()).isNotNull();
        verify(leadActivityRepository, times(1)).save(any(LeadActivity.class));
    }

    @Test
    void create_normalizesEmailAndPhoneBeforeStoring() {
        when(leadRepository.findByDedupeHash(any())).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L));
        when(leadWriter.tryCreate(any())).thenAnswer(inv -> {
            Lead lead = inv.getArgument(0);
            lead.setId(1L);
            return Optional.of(lead);
        });

        CreateLeadRequest request = new CreateLeadRequest("Ada Lovelace", "  ADA@Example.com  ",
                "+1 (555) 000-1111", LeadSource.LANDING_PAGE, "INDIVIDUAL", null, null);
        LeadResponse response = service().create(request, 9L);

        assertThat(response.email()).isEqualTo("ada@example.com");
        assertThat(response.phone()).isEqualTo("15550001111");
    }

    @Test
    void create_duplicateEmailAndPhone_updatesExistingAndLogsOneActivity() {
        Lead existing = new Lead();
        existing.setId(5L);
        existing.setStatus(LeadStatus.CONTACTED);
        when(leadRepository.findByDedupeHash(any())).thenReturn(Optional.of(existing));
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L));

        service().create(createRequest(), 9L);

        verify(leadRepository).save(existing);
        verify(leadActivityRepository, times(1)).save(any(LeadActivity.class));
        assertThat(existing.getStatus()).isEqualTo(LeadStatus.CONTACTED);
        assertThat(existing.getSource()).isEqualTo(LeadSource.LANDING_PAGE);
    }

    @Test
    void create_raceOnInsert_fallsBackToUpdateExistingPathViaFreshTransactionRead() {
        Lead winner = new Lead();
        winner.setId(7L);
        winner.setStatus(LeadStatus.NEW);
        when(leadRepository.findByDedupeHash(any())).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L));
        when(leadWriter.tryCreate(any())).thenReturn(Optional.empty());
        // The race-fallback re-fetch must go through leadWriter (REQUIRES_NEW, a fresh snapshot),
        // never leadRepository directly — the whole point of the fix.
        when(leadWriter.findByDedupeHash(any())).thenReturn(Optional.of(winner));

        LeadResponse response = service().create(createRequest(), 9L);

        assertThat(response.id()).isEqualTo(7L);
        verify(leadRepository).save(winner);
    }

    @Test
    void create_unknownInterestedPlan_throwsNotFound() {
        CreateLeadRequest request = new CreateLeadRequest("Ada Lovelace", "ada@example.com",
                "+1 555 000 1111", LeadSource.LANDING_PAGE, "INDIVIDUAL", null, 42L);
        when(subscriptionPlanRepository.existsById(42L)).thenReturn(false);

        assertThatThrownBy(() -> service().create(request, 9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_NOT_FOUND);
    }

    // --- list ---

    @Test
    void list_unknownAgentUuid_neverMatchesAnyRealAgent() {
        when(userRepository.findByUuid("no-such-uuid")).thenReturn(Optional.empty());
        when(leadRepository.search(any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service().list(null, "no-such-uuid", null, org.springframework.data.domain.PageRequest.of(0, 20));

        verify(leadRepository).search(any(), org.mockito.ArgumentMatchers.eq(-1L), any(), any());
    }

    // --- updateStatus ---

    private Lead leadWithStatus(LeadStatus status) {
        Lead lead = new Lead();
        lead.setId(1L);
        lead.setStatus(status);
        return lead;
    }

    @Test
    void updateStatus_forwardOneStep_succeeds() {
        Lead lead = leadWithStatus(LeadStatus.NEW);
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L));

        LeadResponse response = service().updateStatus(1L,
                new UpdateLeadStatusRequest(LeadStatus.CONTACTED, null, null, null), 9L);

        assertThat(response.status()).isEqualTo(LeadStatus.CONTACTED);
        verify(leadActivityRepository).save(any(LeadActivity.class));
    }

    @Test
    void updateStatus_forwardSkip_throwsPipelineSkip() {
        Lead lead = leadWithStatus(LeadStatus.NEW);
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> service().updateStatus(1L,
                new UpdateLeadStatusRequest(LeadStatus.DEMO_SCHEDULED, null, null, null), 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEAD_PIPELINE_SKIP);
    }

    @Test
    void updateStatus_backwardWithoutReason_throwsReasonRequired() {
        Lead lead = leadWithStatus(LeadStatus.DEMO_SCHEDULED);
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> service().updateStatus(1L,
                new UpdateLeadStatusRequest(LeadStatus.CONTACTED, null, null, null), 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEAD_BACKWARD_REASON_REQUIRED);
    }

    @Test
    void updateStatus_backwardWithReason_succeeds() {
        Lead lead = leadWithStatus(LeadStatus.DEMO_SCHEDULED);
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L));

        LeadResponse response = service().updateStatus(1L,
                new UpdateLeadStatusRequest(LeadStatus.CONTACTED, "Demo no-show, resetting.", null, null), 9L);

        assertThat(response.status()).isEqualTo(LeadStatus.CONTACTED);
    }

    @Test
    void updateStatus_alreadyTerminal_throwsAlreadyTerminal() {
        Lead lead = leadWithStatus(LeadStatus.ENROLLED);
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> service().updateStatus(1L,
                new UpdateLeadStatusRequest(LeadStatus.CONTACTED, "reason", null, null), 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEAD_ALREADY_TERMINAL);
    }

    @Test
    void updateStatus_sameStatus_throwsStatusUnchanged() {
        Lead lead = leadWithStatus(LeadStatus.CONTACTED);
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> service().updateStatus(1L,
                new UpdateLeadStatusRequest(LeadStatus.CONTACTED, null, null, null), 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEAD_STATUS_UNCHANGED);
    }

    @Test
    void updateStatus_toLost_setsLostReasonFromAnyNonTerminalStatus() {
        Lead lead = leadWithStatus(LeadStatus.COUNSELLING_DONE);
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L));

        LeadResponse response = service().updateStatus(1L,
                new UpdateLeadStatusRequest(LeadStatus.LOST, null, "Chose a competitor.", null), 9L);

        assertThat(response.status()).isEqualTo(LeadStatus.LOST);
        assertThat(response.lostReason()).isEqualTo("Chose a competitor.");
    }

    @Test
    void updateStatus_toEnrolled_setsConvertedUser() {
        Lead lead = leadWithStatus(LeadStatus.PAYMENT_PENDING);
        User convertedUser = user(20L);
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(userRepository.findByUuid(convertedUser.getUuid())).thenReturn(Optional.of(convertedUser));
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L));

        LeadResponse response = service().updateStatus(1L,
                new UpdateLeadStatusRequest(LeadStatus.ENROLLED, null, null, convertedUser.getUuid()), 9L);

        assertThat(response.status()).isEqualTo(LeadStatus.ENROLLED);
        assertThat(response.convertedUserUuid()).isEqualTo(convertedUser.getUuid());
    }

    @Test
    void updateStatus_enrolledWithUnknownUuid_throwsUserNotFound() {
        Lead lead = leadWithStatus(LeadStatus.PAYMENT_PENDING);
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(userRepository.findByUuid("no-such-uuid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateStatus(1L,
                new UpdateLeadStatusRequest(LeadStatus.ENROLLED, null, null, "no-such-uuid"), 9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updateStatus_unknownLead_throwsNotFound() {
        when(leadRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateStatus(99L,
                new UpdateLeadStatusRequest(LeadStatus.CONTACTED, null, null, null), 9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEAD_NOT_FOUND);
    }

    // --- addActivity ---

    @Test
    void addActivity_happyPath_savesAndReturnsResponse() {
        Lead lead = leadWithStatus(LeadStatus.CONTACTED);
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L));

        LeadActivityResponse response = service().addActivity(1L,
                new AddLeadActivityRequest(LeadActivityType.CALL, "NO_ANSWER", "Tried twice.",
                        null, Instant.now(), null), 9L);

        assertThat(response.leadId()).isEqualTo(1L);
        assertThat(response.activityType()).isEqualTo(LeadActivityType.CALL);
        verify(leadActivityRepository).save(any(LeadActivity.class));
    }

    @Test
    void addActivity_whatsAppType_dispatchesTemplateToTheLeadsPhone() {
        Lead lead = leadWithStatus(LeadStatus.CONTACTED);
        lead.setPhone("15550001111");
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L));

        service().addActivity(1L, new AddLeadActivityRequest(LeadActivityType.WHATSAPP, "SENT",
                "Following up on the demo.", null, Instant.now(), "demo_followup_v1"), 9L);

        verify(leadActivityRepository).save(any(LeadActivity.class));
        verify(leadWhatsAppSender).sendAfterCommit("demo_followup_v1", "15550001111");
    }

    @Test
    void addActivity_unknownLead_throwsNotFound() {
        when(leadRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().addActivity(99L,
                new AddLeadActivityRequest(LeadActivityType.CALL, null, null, null, Instant.now(), null), 9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEAD_NOT_FOUND);
    }

    // --- myTargets ---

    @Test
    void myTargets_found_returnsResponse() {
        SalesTarget target = new SalesTarget();
        target.setId(1L);
        target.setAgent(user(9L));
        target.setPeriodMonth(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1));
        target.setCallsTarget(50);
        target.setCallsMade(10);
        target.setConversionsTarget(5);
        target.setConversionsMade(1);
        target.setRevenueTarget(new BigDecimal("100000.00"));
        target.setRevenueAchieved(new BigDecimal("20000.00"));
        when(salesTargetRepository.findByAgentIdAndPeriodMonth(any(), any())).thenReturn(Optional.of(target));

        SalesTargetResponse response = service().myTargets(9L);

        assertThat(response.callsMade()).isEqualTo(10);
    }

    @Test
    void myTargets_notFound_throwsNotFound() {
        when(salesTargetRepository.findByAgentIdAndPeriodMonth(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().myTargets(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SALES_TARGET_NOT_FOUND);
    }
}
