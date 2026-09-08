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
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationService;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.placement.dto.PlacementCandidateOption;
import com.moriah.skillhub.placement.dto.PlacementResponse;
import com.moriah.skillhub.placement.dto.UpdatePlacementRequest;
import com.moriah.skillhub.placement.entity.Placement;
import com.moriah.skillhub.placement.entity.PlacementStage;
import com.moriah.skillhub.placement.repository.PlacementRepository;
import com.moriah.skillhub.user.UserService;
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
 * auto-created by {@code TalentService.createRequest} the moment a client shortlists a candidate
 * (there is no separate HR "approve request" gate), then advanced through {@link PlacementStage}
 * by the three personas — see that enum's Javadoc for the per-stage ownership rules this service
 * enforces. Stage regressions are rejected; {@code details} is a free-form JSON object merged on
 * each update. Each stage change fires an in-app notification to whoever the ball moves to
 * ({@link #notifyStageChange}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlacementService {

    private final PlacementRepository placementRepository;
    private final UserRepository userRepository;
    private final BatchStudentRepository batchStudentRepository;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final UserService userService;

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

        Map<Long, User> users = usersById(List.of(placement));
        if (target != current) {
            notifyStageChange(placement, target, callerUserId, users);
        }
        return toResponse(placement, users);
    }

    /**
     * In-app notification to whoever the ball is now with. Each transition hands off to a
     * different actor: the client drives the technical rounds, HR the HR round / documents /
     * offer, the candidate the final decision — so a stage change is exactly when someone new
     * needs to be told. Fired {@code enqueueAfterCommit} so a notification is never sent for a
     * transition that rolls back. One template ({@code PLACEMENT_STAGE_CHANGED}) with an {@code
     * audience} tag in the payload — the frontend renderer phrases it for the reader.
     * <p>
     * Intermediate markers ({@code TECHNICAL_COMPLETED}, {@code HR_COMPLETED}) and the pipeline
     * open ({@code SHORTLISTED}) notify no one; the "docs verified" / "offer sent" steps happen
     * inside a stage via {@code details} and are notified from the frontend action instead.
     */
    private void notifyStageChange(Placement p, PlacementStage stage, Long actorUserId, Map<Long, User> users) {
        Long candidateId = p.getCandidateId();
        Long clientId = p.getClientId();
        String candidateName = users.get(candidateId) == null ? "" : users.get(candidateId).getFullName();
        String clientName = users.get(clientId) == null ? "" : users.get(clientId).getFullName();

        switch (stage) {
            case TECHNICAL_SCHEDULED, HR_SCHEDULED, HR_APPROVED, DOCUMENT_VERIFICATION, CLIENT_SIGNED ->
                    notifyOne(candidateId, p, stage, "candidate", candidateName, clientName);
            case TECHNICAL_APPROVED, STUDENT_SIGNED ->
                    userService.findUserIdsByRole(RoleCode.HR_MANAGER)
                            .forEach(hr -> notifyOne(hr, p, stage, "hr", candidateName, clientName));
            case OFFER_CREATED ->
                    notifyOne(clientId, p, stage, "client", candidateName, clientName);
            case PLACED -> {
                notifyOne(candidateId, p, stage, "candidate", candidateName, clientName);
                notifyOne(clientId, p, stage, "client", candidateName, clientName);
            }
            case REJECTED -> {
                if (!candidateId.equals(actorUserId)) {
                    notifyOne(candidateId, p, stage, "candidate", candidateName, clientName);
                }
                if (!clientId.equals(actorUserId)) {
                    notifyOne(clientId, p, stage, "client", candidateName, clientName);
                }
                userService.findUserIdsByRole(RoleCode.HR_MANAGER).stream()
                        .filter(hr -> !hr.equals(actorUserId))
                        .forEach(hr -> notifyOne(hr, p, stage, "hr", candidateName, clientName));
            }
            case SHORTLISTED, TECHNICAL_COMPLETED, HR_COMPLETED -> { /* no recipient */ }
        }
    }

    private void notifyOne(Long userId, Placement p, PlacementStage stage, String audience,
            String candidateName, String clientName) {
        notificationService.enqueueAfterCommit(userId, NotificationChannel.IN_APP, "PLACEMENT_STAGE_CHANGED", Map.of(
                "placementId", p.getId(),
                "stage", stage.name(),
                "audience", audience,
                "candidateName", Objects.toString(candidateName, ""),
                "clientName", Objects.toString(clientName, "")));
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
            // The candidate may DECLINE the offer, but only once it's actually in front of them
            // (the client has signed) — not bail out mid-interview. Client / HR can reject at
            // any non-terminal stage. `placement.getStage()` here is still the current stage —
            // `update` mutates it only after this check passes.
            case REJECTED -> isThisClient || isHr
                    || (isThisCandidate && placement.getStage() == PlacementStage.CLIENT_SIGNED);
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
