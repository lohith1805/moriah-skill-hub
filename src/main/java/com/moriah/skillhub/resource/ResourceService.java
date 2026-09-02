package com.moriah.skillhub.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.resource.dto.CreateResourceRequest;
import com.moriah.skillhub.resource.dto.LearningResourceResponse;
import com.moriah.skillhub.resource.dto.UpdateResourceRequest;
import com.moriah.skillhub.resource.entity.LearningResource;
import com.moriah.skillhub.resource.entity.ResourceCategory;
import com.moriah.skillhub.resource.repository.LearningResourceRepository;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resource Library (gap B1.6). A curated catalogue of external learning links: any authenticated
 * user browses, staff (TRAINER_PM / DEVELOPER / BUSINESS_ANALYST / ADMIN — gated on the
 * controller) curate. {@code UserRepository} is injected directly to resolve creator uuids —
 * {@code User} is the established shared identity primitive every module reads directly (same as
 * {@code CertificateService} / {@code LeadService} / {@code AdminUserService}).
 * <p>
 * {@code tags} round-trips through {@code ObjectMapper} exactly as {@code ProjectService} handles
 * {@code techStack} — a bad stored value degrades to an empty list, never a 500.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceService {

    private static final Set<String> CURATOR_ROLES = Set.of(
            RoleCode.TRAINER_PM.name(), RoleCode.DEVELOPER.name(),
            RoleCode.BUSINESS_ANALYST.name(), RoleCode.ADMIN.name());

    private final LearningResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /** {@code includeInactive} is a curator affordance — a plain browsing user always sees the
     * active-only view regardless of the flag they send. */
    @Transactional(readOnly = true)
    public PageResponse<LearningResourceResponse> list(boolean includeInactive, ResourceCategory category,
                                                        String search, Pageable pageable) {
        boolean effectiveIncludeInactive = includeInactive && isCurator();
        Page<LearningResource> page = resourceRepository.search(
                !effectiveIncludeInactive, category, blankToNull(search), pageable);
        Map<Long, String> uuidByUserId = resolveCreatorUuids(page.getContent());
        return PageResponse.from(page.map(r -> toResponse(r, uuidByUserId)));
    }

    @Transactional(readOnly = true)
    public LearningResourceResponse get(Long id) {
        LearningResource resource = requireResource(id);
        return toResponse(resource, resolveCreatorUuids(List.of(resource)));
    }

    @Transactional
    public LearningResourceResponse create(CreateResourceRequest request, Long callerUserId) {
        LearningResource resource = new LearningResource();
        resource.setTitle(request.title());
        resource.setDescription(blankToNull(request.description()));
        resource.setCategory(request.category());
        resource.setUrl(request.url());
        resource.setTags(toJson(request.tags()));
        resource.setCreatedBy(callerUserId);
        resource.setActive(true);
        resourceRepository.save(resource);

        log.info("[resources] {} created resource {} ({})", callerUserId, resource.getId(), resource.getCategory());
        return toResponse(resource, resolveCreatorUuids(List.of(resource)));
    }

    @Transactional
    public LearningResourceResponse update(Long id, UpdateResourceRequest request, Long callerUserId) {
        LearningResource resource = requireResource(id);
        requireCreatorOrAdmin(resource, callerUserId);

        resource.setTitle(request.title());
        resource.setDescription(blankToNull(request.description()));
        resource.setCategory(request.category());
        resource.setUrl(request.url());
        resource.setTags(toJson(request.tags()));
        resource.setActive(request.active());

        return toResponse(resource, resolveCreatorUuids(List.of(resource)));
    }

    /** Deactivate, not row-delete — a resource that has been shared in a batch channel should
     * not 404 later. Idempotent. */
    @Transactional
    public void deactivate(Long id, Long callerUserId) {
        LearningResource resource = requireResource(id);
        requireCreatorOrAdmin(resource, callerUserId);
        resource.setActive(false);
    }

    private void requireCreatorOrAdmin(LearningResource resource, Long callerUserId) {
        if (SecurityUtils.currentUserRoles().contains(RoleCode.ADMIN.name())) {
            return;
        }
        if (!resource.getCreatedBy().equals(callerUserId)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }

    private boolean isCurator() {
        return SecurityUtils.currentUserRoles().stream().anyMatch(CURATOR_ROLES::contains);
    }

    private LearningResource requireResource(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, id));
    }

    private Map<Long, String> resolveCreatorUuids(List<LearningResource> resources) {
        List<Long> ids = resources.stream().map(LearningResource::getCreatedBy).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUuid));
    }

    private LearningResourceResponse toResponse(LearningResource r, Map<Long, String> uuidByUserId) {
        return new LearningResourceResponse(
                r.getId(),
                r.getTitle(),
                r.getDescription(),
                r.getCategory(),
                r.getUrl(),
                fromJson(r.getTags()),
                uuidByUserId.get(r.getCreatedBy()),
                r.isActive(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }

    private String toJson(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tags.stream().map(Function.identity()).distinct().toList());
        } catch (Exception e) {
            log.warn("[resources] failed to serialize tags, storing null", e);
            return null;
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            CollectionType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, String.class);
            return objectMapper.readValue(json, listType);
        } catch (Exception e) {
            log.warn("[resources] failed to parse stored tags JSON, treating as empty", e);
            return List.of();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
