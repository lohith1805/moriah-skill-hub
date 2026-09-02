package com.moriah.skillhub.talent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.AuthenticatedPrincipal;
import com.moriah.skillhub.talent.dto.CreateRecruitmentRequestRequest;
import com.moriah.skillhub.talent.dto.DecideRecruitmentRequestRequest;
import com.moriah.skillhub.talent.dto.RecruitmentRequestResponse;
import com.moriah.skillhub.talent.dto.TalentPoolCandidateResponse;
import com.moriah.skillhub.talent.entity.EngagementType;
import com.moriah.skillhub.talent.entity.RecruitmentRequest;
import com.moriah.skillhub.talent.entity.RecruitmentRequestStatus;
import com.moriah.skillhub.talent.repository.RecruitmentRequestRepository;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserProfile;
import com.moriah.skillhub.user.repository.UserProfileRepository;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private RecruitmentRequestRepository requestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private com.moriah.skillhub.common.audit.AuditLogService auditLogService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TalentService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId, String... roles) {
        AuthenticatedPrincipal p = new AuthenticatedPrincipal(userId, "uuid-" + userId, List.of(roles));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(p, null));
    }

    private User user(long id, String uuid, String name) {
        User u = new User();
        u.setId(id);
        u.setUuid(uuid);
        u.setFullName(name);
        return u;
    }

    private RecruitmentRequest request(long id) {
        RecruitmentRequest r = new RecruitmentRequest();
        r.setId(id);
        r.setCandidateId(20L);
        r.setRequestedBy(30L);
        r.setRoleTitle("Backend Engineer");
        r.setEngagementType(EngagementType.FULL_TIME);
        r.setStatus(RecruitmentRequestStatus.PENDING);
        return r;
    }

    @Test
    void browse_parsesSkillsJsonToList() {
        UserProfile p = new UserProfile();
        p.setUser(user(20L, "cand-uuid", "Cand One"));
        p.setCurrentTitle("Junior Dev");
        p.setSkills("[\"Java\",\"Spring\"]");
        p.setPortfolioSlug("cand-one");
        when(userProfileRepository.searchTalentPool(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1));

        PageResponse<TalentPoolCandidateResponse> page = service.browse(null, null, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).uuid()).isEqualTo("cand-uuid");
        assertThat(page.content().get(0).skills()).containsExactly("Java", "Spring");
    }

    @Test
    void createRequest_resolvesCandidateAndLandsPending() {
        when(userRepository.findByUuid("cand-uuid")).thenReturn(Optional.of(user(20L, "cand-uuid", "Cand One")));
        when(requestRepository.save(any(RecruitmentRequest.class))).thenAnswer(inv -> {
            RecruitmentRequest r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(userRepository.findAllById(any())).thenReturn(List.of(
                user(20L, "cand-uuid", "Cand One"), user(30L, "client-uuid", "Client One")));

        RecruitmentRequestResponse response = service.createRequest(new CreateRecruitmentRequestRequest(
                "cand-uuid", "Backend Engineer", EngagementType.CONTRACT, "Interested for a 6-month contract."), 30L);

        ArgumentCaptor<RecruitmentRequest> captor = ArgumentCaptor.forClass(RecruitmentRequest.class);
        verify(requestRepository).save(captor.capture());
        assertThat(captor.getValue().getCandidateId()).isEqualTo(20L);
        assertThat(captor.getValue().getRequestedBy()).isEqualTo(30L);
        assertThat(captor.getValue().getStatus()).isEqualTo(RecruitmentRequestStatus.PENDING);
        assertThat(response.candidateUuid()).isEqualTo("cand-uuid");
        assertThat(response.requestedByUuid()).isEqualTo("client-uuid");
    }

    @Test
    void createRequest_unknownCandidate_throwsNotFound() {
        when(userRepository.findByUuid("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRequest(new CreateRecruitmentRequestRequest(
                "ghost", "Role", EngagementType.PROJECT, null), 30L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void listRequests_client_seesOnlyOwn() {
        authenticateAs(30L, RoleCode.CLIENT.name());
        when(requestRepository.search(isNull(), eq(30L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.listRequests(null, 30L, PageRequest.of(0, 20));

        verify(requestRepository).search(isNull(), eq(30L), any(Pageable.class));
    }

    @Test
    void listRequests_admin_seesAll() {
        authenticateAs(1L, RoleCode.ADMIN.name());
        when(requestRepository.search(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.listRequests(null, 1L, PageRequest.of(0, 20));

        verify(requestRepository).search(isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void decide_fromPending_recordsDecider() {
        RecruitmentRequest r = request(3L);
        when(requestRepository.findById(3L)).thenReturn(Optional.of(r));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        service.decide(3L, new DecideRecruitmentRequestRequest(
                RecruitmentRequestStatus.APPROVED, "Great fit, proceed."), 1L);

        assertThat(r.getStatus()).isEqualTo(RecruitmentRequestStatus.APPROVED);
        assertThat(r.getDecidedBy()).isEqualTo(1L);
        assertThat(r.getDecidedAt()).isNotNull();
        assertThat(r.getDecisionNote()).isEqualTo("Great fit, proceed.");
    }

    @Test
    void decide_alreadyDecided_throwsBusinessRule() {
        RecruitmentRequest r = request(3L);
        r.setStatus(RecruitmentRequestStatus.REJECTED);
        when(requestRepository.findById(3L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.decide(3L, new DecideRecruitmentRequestRequest(
                RecruitmentRequestStatus.APPROVED, null), 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void decideRequest_rejectsNonTerminalStatusAtConstruction() {
        assertThatThrownBy(() -> new DecideRecruitmentRequestRequest(RecruitmentRequestStatus.PENDING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
