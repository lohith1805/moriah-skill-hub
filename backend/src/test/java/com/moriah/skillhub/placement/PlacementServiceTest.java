package com.moriah.skillhub.placement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.batch.repository.BatchStudentRepository;
import com.moriah.skillhub.common.security.AuthenticatedPrincipal;
import com.moriah.skillhub.placement.dto.UpdatePlacementRequest;
import com.moriah.skillhub.placement.entity.Placement;
import com.moriah.skillhub.placement.entity.PlacementStage;
import com.moriah.skillhub.placement.repository.PlacementRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlacementServiceTest {

    @Mock
    private PlacementRepository placementRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BatchStudentRepository batchStudentRepository;

    private PlacementService service() {
        return new PlacementService(placementRepository, userRepository, batchStudentRepository, new ObjectMapper());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId, List<String> roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new AuthenticatedPrincipal(userId, "uuid-" + userId, roles), null));
    }

    private Placement placement(PlacementStage stage) {
        Placement p = new Placement();
        p.setId(1L);
        p.setRecruitmentRequestId(50L);
        p.setCandidateId(200L); // student
        p.setClientId(100L);    // client
        p.setStage(stage);
        return p;
    }

    private User u(long id) {
        User user = new User();
        user.setId(id);
        user.setUuid("uuid-" + id);
        return user;
    }

    @Test
    void update_clientAdvancesTechnicalRound_merged() {
        authenticateAs(100L, List.of("CLIENT"));
        when(placementRepository.findById(1L)).thenReturn(Optional.of(placement(PlacementStage.SHORTLISTED)));
        when(userRepository.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
                .thenReturn(List.of(u(100L), u(200L)));

        var res = service().update(1L,
                new UpdatePlacementRequest(PlacementStage.TECHNICAL_SCHEDULED, Map.of("meetingLink", "https://x")),
                100L);

        assertThat(res.stage()).isEqualTo(PlacementStage.TECHNICAL_SCHEDULED);
        assertThat(res.details()).containsEntry("meetingLink", "https://x");
    }

    @Test
    void update_backwardStage_rejected() {
        authenticateAs(100L, List.of("CLIENT"));
        when(placementRepository.findById(1L)).thenReturn(Optional.of(placement(PlacementStage.TECHNICAL_APPROVED)));

        assertThatThrownBy(() -> service().update(1L,
                new UpdatePlacementRequest(PlacementStage.TECHNICAL_SCHEDULED, null), 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void update_clientTriesToSetHrStage_forbidden() {
        authenticateAs(100L, List.of("CLIENT"));
        when(placementRepository.findById(1L)).thenReturn(Optional.of(placement(PlacementStage.TECHNICAL_APPROVED)));

        assertThatThrownBy(() -> service().update(1L,
                new UpdatePlacementRequest(PlacementStage.HR_SCHEDULED, null), 100L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_ROLE);
    }

    @Test
    void update_studentSignsAfterClient() {
        authenticateAs(200L, List.of("STUDENT"));
        when(placementRepository.findById(1L)).thenReturn(Optional.of(placement(PlacementStage.CLIENT_SIGNED)));
        when(userRepository.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
                .thenReturn(List.of(u(100L), u(200L)));

        var res = service().update(1L, new UpdatePlacementRequest(PlacementStage.STUDENT_SIGNED, null), 200L);

        assertThat(res.stage()).isEqualTo(PlacementStage.STUDENT_SIGNED);
    }

    @Test
    void update_unrelatedUser_cannotView() {
        authenticateAs(999L, List.of("CLIENT"));
        when(placementRepository.findById(1L)).thenReturn(Optional.of(placement(PlacementStage.SHORTLISTED)));

        assertThatThrownBy(() -> service().update(1L,
                new UpdatePlacementRequest(PlacementStage.TECHNICAL_SCHEDULED, null), 999L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    void update_fromTerminalStage_rejected() {
        authenticateAs(9L, List.of("ADMIN"));
        when(placementRepository.findById(1L)).thenReturn(Optional.of(placement(PlacementStage.PLACED)));

        assertThatThrownBy(() -> service().update(1L,
                new UpdatePlacementRequest(PlacementStage.REJECTED, null), 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void createForApprovedRequest_isIdempotent() {
        when(placementRepository.existsByRecruitmentRequestId(50L)).thenReturn(true);

        service().createForApprovedRequest(50L, 200L, 100L);

        org.mockito.Mockito.verify(placementRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
