package com.moriah.skillhub.talent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.talent.dto.CreateRecruitmentRequestRequest;
import com.moriah.skillhub.talent.dto.DecideRecruitmentRequestRequest;
import com.moriah.skillhub.talent.dto.RecruitmentRequestResponse;
import com.moriah.skillhub.talent.dto.TalentPoolCandidateResponse;
import com.moriah.skillhub.talent.entity.RecruitmentRequest;
import com.moriah.skillhub.talent.entity.RecruitmentRequestStatus;
import com.moriah.skillhub.talent.repository.RecruitmentRequestRepository;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserProfile;
import com.moriah.skillhub.user.repository.UserProfileRepository;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Client Talent Pool + recruitment requests (gap B1.9). The pool browse is a read over
 * {@code user_profiles}; recruitment requests are their own object. A CLIENT sees only their own
 * requests; ADMIN/HR_MANAGER see all and decide them (privilege resolved from the JWT roles via
 * {@link SecurityUtils}, no DB round trip).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TalentService {

    private final UserProfileRepository userProfileRepository;
    private final RecruitmentRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<TalentPoolCandidateResponse> browse(String search, String skill, Pageable pageable) {
        Page<UserProfile> page = userProfileRepository.searchTalentPool(blankToNull(search), blankToNull(skill), pageable);
        return PageResponse.from(page.map(this::toCandidate));
    }

    @Transactional
    public RecruitmentRequestResponse createRequest(CreateRecruitmentRequestRequest request, Long callerUserId) {
        User candidate = userRepository.findByUuid(request.candidateUuid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, request.candidateUuid()));

        RecruitmentRequest entity = new RecruitmentRequest();
        entity.setCandidateId(candidate.getId());
        entity.setRequestedBy(callerUserId);
        entity.setRoleTitle(request.roleTitle());
        entity.setEngagementType(request.engagementType());
        entity.setMessage(blankToNull(request.message()));
        entity.setStatus(RecruitmentRequestStatus.PENDING);
        requestRepository.save(entity);

        auditLogService.record(callerUserId, "RECRUITMENT_REQUEST_CREATED", "RecruitmentRequest",
                entity.getId(), null, candidate.getUuid());
        log.info("[recruitment] {} requested {} for candidate {}", callerUserId, entity.getEngagementType(), candidate.getUuid());
        return toResponse(entity, usersById(List.of(entity)));
    }

    @Transactional(readOnly = true)
    public PageResponse<RecruitmentRequestResponse> listRequests(RecruitmentRequestStatus status,
            Long callerUserId, Pageable pageable) {
        Long requestedBy = isPrivileged() ? null : callerUserId;
        Page<RecruitmentRequest> page = requestRepository.search(status, requestedBy, pageable);
        Map<Long, User> users = usersById(page.getContent());
        return PageResponse.from(page.map(r -> toResponse(r, users)));
    }

    /** {@code PUT /api/v1/recruitment-requests/{id}/status} — ADMIN/HR_MANAGER only (controller).
     * A decision is only valid from {@code PENDING}; a second decision is a {@code
     * BUSINESS_RULE_VIOLATION}, matching the codebase's one-off state-guard precedent. */
    @Transactional
    public RecruitmentRequestResponse decide(Long id, DecideRecruitmentRequestRequest request, Long callerUserId) {
        RecruitmentRequest entity = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RECRUITMENT_REQUEST_NOT_FOUND, id));
        if (entity.getStatus() != RecruitmentRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This recruitment request has already been decided.");
        }

        entity.setStatus(request.status());
        entity.setDecisionNote(blankToNull(request.decisionNote()));
        entity.setDecidedBy(callerUserId);
        entity.setDecidedAt(Instant.now());

        auditLogService.record(callerUserId, "RECRUITMENT_REQUEST_DECIDED", "RecruitmentRequest",
                entity.getId(), null, request.status());
        return toResponse(entity, usersById(List.of(entity)));
    }

    private boolean isPrivileged() {
        var roles = SecurityUtils.currentUserRoles();
        return roles.contains(RoleCode.ADMIN.name()) || roles.contains(RoleCode.HR_MANAGER.name());
    }

    private Map<Long, User> usersById(List<RecruitmentRequest> requests) {
        List<Long> ids = requests.stream()
                .flatMap(r -> Stream.of(r.getCandidateId(), r.getRequestedBy(), r.getDecidedBy()))
                .filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private TalentPoolCandidateResponse toCandidate(UserProfile p) {
        return new TalentPoolCandidateResponse(
                p.getUser().getUuid(),
                p.getUser().getFullName(),
                p.getCurrentTitle(),
                p.getLocation(),
                p.getExperienceLevel(),
                p.getYearsExperience(),
                parseSkills(p.getSkills()),
                p.getPortfolioSlug(),
                p.getBio());
    }

    private RecruitmentRequestResponse toResponse(RecruitmentRequest r, Map<Long, User> users) {
        User candidate = users.get(r.getCandidateId());
        User requester = users.get(r.getRequestedBy());
        User decider = r.getDecidedBy() == null ? null : users.get(r.getDecidedBy());
        return new RecruitmentRequestResponse(
                r.getId(),
                candidate == null ? null : candidate.getUuid(),
                candidate == null ? null : candidate.getFullName(),
                requester == null ? null : requester.getUuid(),
                r.getRoleTitle(),
                r.getEngagementType(),
                r.getMessage(),
                r.getStatus(),
                r.getDecisionNote(),
                decider == null ? null : decider.getUuid(),
                r.getDecidedAt(),
                r.getCreatedAt());
    }

    private List<String> parseSkills(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            CollectionType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, String.class);
            return objectMapper.readValue(json, listType);
        } catch (Exception e) {
            log.warn("[talent-pool] unparseable skills JSON on a profile, treating as empty");
            return List.of();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
