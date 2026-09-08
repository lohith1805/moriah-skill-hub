package com.moriah.skillhub.placement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.batch.entity.BatchStudentStatus;
import com.moriah.skillhub.batch.repository.BatchStudentRepository;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.placement.dto.PlacementCandidateOption;
import com.moriah.skillhub.placement.dto.PlacementResponse;
import com.moriah.skillhub.placement.dto.UpdatePlacementRequest;
import com.moriah.skillhub.placement.entity.Placement;
import com.moriah.skillhub.placement.entity.PlacementStage;
import com.moriah.skillhub.placement.repository.PlacementRepository;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The client-placement pipeline (frontend-integration Part A). A {@link Placement} row is
 * auto-created by {@code TalentService.decide} when a recruitment request is APPROVED, then
 * advanced through {@link PlacementStage} by the three personas — see that enum's Javadoc for the
 * per-stage ownership rules this service enforces. Stage regressions are rejected; {@code
 * details} is a free-form JSON object merged on each update.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlacementService {

    private final PlacementRepository placementRepository;
    private final UserRepository userRepository;
    private final BatchStudentRepository batchStudentRepository;
    private final ObjectMapper objectMapper;

    /** Called from {@code TalentService.decide} on an APPROVED request — primitives only, so this
     * module owns no {@code talent/} type. Idempotent. */
    @Transactional
    public void createForApprovedRequest(Long recruitmentRequestId, Long candidateUserId, Long clientUserId) {
        if (placementRepository.existsByRecruitmentRequestId(recruitmentRequestId)) {
            return;
        }
        Placement placement = new Placement();
        placement.setRecruitmentRequestId(recruitmentRequestId);
        placement.setCandidateId(candidateUserId);
        placement.setClientId(clientUserId);
        placement.setStage(PlacementStage.SHORTLISTED);
        placementRepository.save(placement);
        log.info("[placement] created for approved recruitment request {}", recruitmentRequestId);
    }

    @Transactional(readOnly = true)
    public PageResponse<PlacementResponse> list(PlacementStage stage, Long callerUserId, Pageable pageable) {
        var roles = SecurityUtils.currentUserRoles();
        boolean privileged = roles.contains(RoleCode.HR_MANAGER.name()) || roles.contains(RoleCode.ADMIN.name());
        Long candidateId = roles.contains(RoleCode.STUDENT.name()) && !privileged ? callerUserId : null;
        Long clientId = roles.contains(RoleCode.CLIENT.name()) && !privileged ? callerUserId : null;

        Page<Placement> page = placementRepository.search(candidateId, clientId, stage, pageable);
        Map<Long, User> users = usersById(page.getContent());
        return PageResponse.from(page.map(p -> toResponse(p, users)));
    }

    @Transactional(readOnly = true)
    public PlacementResponse get(Long id, Long callerUserId) {
        Placement placement = require(id);
        requireViewer(placement, callerUserId);
        return toResponse(placement, usersById(List.of(placement)));
    }

    /** {@code GET /api/v1/placements/candidates} (HR_MANAGER/ADMIN) — every non-REJECTED
     * shortlisting as a picker option for letter generation. {@code graduated} is resolved for
     * the whole set in one batch query. */
    @Transactional(readOnly = true)
    public List<PlacementCandidateOption> candidateOptions() {
        List<Placement> placements = placementRepository.findByStageNotOrderByUpdatedAtDesc(PlacementStage.REJECTED);
        if (placements.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = usersById(placements);
        List<Long> candidateIds = placements.stream().map(Placement::getCandidateId).filter(Objects::nonNull).distinct().toList();
        java.util.Set<Long> graduatedIds = candidateIds.isEmpty() ? java.util.Set.of()
                : new java.util.HashSet<>(batchStudentRepository.findUserIdsByStatusAndUserIdIn(BatchStudentStatus.GRADUATED, candidateIds));
        return placements.stream().map(p -> {
            User candidate = users.get(p.getCandidateId());
            User client = users.get(p.getClientId());
            return new PlacementCandidateOption(
                    p.getId(),
                    candidate == null ? null : candidate.getUuid(),
                    candidate == null ? null : candidate.getFullName(),
                    client == null ? null : client.getUuid(),
                    client == null ? null : client.getFullName(),
                    p.getStage(),
                    graduatedIds.contains(p.getCandidateId()),
                    readDetails(p.getDetails()));
        }).toList();
    }

    @Transactional
    public PlacementResponse update(Long id, UpdatePlacementRequest request, Long callerUserId) {
        Placement placement = require(id);
        requireViewer(placement, callerUserId);

        PlacementStage current = placement.getStage();
        PlacementStage target = request.stage();

        if (target != current) {
            if (current.isTerminal()) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "This placement is already " + current + " and cannot be changed.");
            }
            boolean forward = target.ordinal() > current.ordinal();
            if (!forward && target != PlacementStage.REJECTED) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "A placement cannot move backwards (" + current + " -> " + target + ").");
            }
            requireOwnerForStage(target, placement, callerUserId);
        }

        placement.setStage(target);
        placement.setDetails(mergeDetails(placement.getDetails(), request.details()));
        return toResponse(placement, usersById(List.of(placement)));
    }

    // --- ownership -----------------------------------------------------

    /** A viewer is the candidate, the requesting client, or any HR_MANAGER/ADMIN. */
    private void requireViewer(Placement placement, Long callerUserId) {
        var roles = SecurityUtils.currentUserRoles();
        boolean privileged = roles.contains(RoleCode.HR_MANAGER.name()) || roles.contains(RoleCode.ADMIN.name());
        if (privileged || callerUserId.equals(placement.getCandidateId()) || callerUserId.equals(placement.getClientId())) {
            return;
        }
        throw new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
    }

    private void requireOwnerForStage(PlacementStage target, Placement placement, Long callerUserId) {
        var roles = SecurityUtils.currentUserRoles();
        boolean isHr = roles.contains(RoleCode.HR_MANAGER.name()) || roles.contains(RoleCode.ADMIN.name());
        boolean isThisClient = callerUserId.equals(placement.getClientId());
        boolean isThisCandidate = callerUserId.equals(placement.getCandidateId());

        boolean allowed = switch (target) {
            case TECHNICAL_SCHEDULED, TECHNICAL_COMPLETED, TECHNICAL_APPROVED, CLIENT_SIGNED -> isThisClient;
            case HR_SCHEDULED, HR_COMPLETED, HR_APPROVED, DOCUMENT_VERIFICATION, OFFER_CREATED, PLACED -> isHr;
            case STUDENT_SIGNED -> isThisCandidate;
            case REJECTED -> isThisClient || isHr;
            case SHORTLISTED -> isHr || isThisClient;
        };
        if (!allowed) {
            throw new ForbiddenOperationException(ErrorCode.INSUFFICIENT_ROLE);
        }
    }

    // --- mapping / json ---------------------------------------------

    private Placement require(Long id) {
        return placementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PLACEMENT_NOT_FOUND, id));
    }

    private Map<Long, User> usersById(List<Placement> placements) {
        List<Long> ids = placements.stream()
                .flatMap(p -> Stream.of(p.getCandidateId(), p.getClientId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private PlacementResponse toResponse(Placement p, Map<Long, User> users) {
        User candidate = users.get(p.getCandidateId());
        User client = users.get(p.getClientId());
        return new PlacementResponse(
                p.getId(),
                p.getRecruitmentRequestId(),
                candidate == null ? null : candidate.getUuid(),
                candidate == null ? null : candidate.getFullName(),
                client == null ? null : client.getUuid(),
                client == null ? null : client.getFullName(),
                p.getStage(),
                readDetails(p.getDetails()),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }

    private Map<String, Object> readDetails(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            log.warn("[placement] unparseable details JSON on placement, treating as empty");
            return Map.of();
        }
    }

    /** Merge {@code patch} into the stored object; a key mapped to {@code null} is removed. */
    private String mergeDetails(String currentJson, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>(readDetails(currentJson));
        if (patch != null) {
            patch.forEach((k, v) -> {
                if (v == null) {
                    merged.remove(k);
                } else {
                    merged.put(k, v);
                }
            });
        }
        if (merged.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize placement details", e);
        }
    }
}
