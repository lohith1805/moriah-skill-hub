package com.moriah.skillhub.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.project.dto.ChallengeResponse;
import com.moriah.skillhub.project.dto.CreateProjectRequest;
import com.moriah.skillhub.project.dto.ProjectAssetResponse;
import com.moriah.skillhub.project.dto.ProjectResponse;
import com.moriah.skillhub.project.dto.UpdateProjectRequest;
import com.moriah.skillhub.project.entity.AssetType;
import com.moriah.skillhub.project.entity.BugChallenge;
import com.moriah.skillhub.project.entity.Project;
import com.moriah.skillhub.project.entity.ProjectAsset;
import com.moriah.skillhub.project.entity.ProjectDifficulty;
import com.moriah.skillhub.project.entity.ProjectStatus;
import com.moriah.skillhub.project.repository.BugChallengeRepository;
import com.moriah.skillhub.project.repository.ProjectAssetRepository;
import com.moriah.skillhub.project.repository.ProjectRepository;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * build-plan.md feature 15. {@code createdBy} is the established shared-kernel {@code User}
 * exception (code-standards.md's own canonical {@code SprintService} example); {@code Project} is
 * otherwise same-package with {@code ProjectAsset}/{@code BugChallenge}, no cross-package
 * association needed for either.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(Constants.PRESIGNED_URL_TTL_MINUTES);

    private final ProjectRepository projectRepository;
    private final ProjectAssetRepository projectAssetRepository;
    private final BugChallengeRepository bugChallengeRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;
    private final ProjectWriter projectWriter;

    @Transactional
    public ProjectResponse create(Long callerUserId, CreateProjectRequest request) {
        User creator = requireUser(callerUserId);

        Project project = new Project();
        project.setTitle(request.title());
        project.setDescription(request.description());
        project.setTechStack(toJson(request.techStack()));
        project.setDifficulty(request.difficulty());
        project.setDomain(request.domain());
        project.setStarterRepoUrl(request.starterRepoUrl());
        project.setVersion(request.version());
        project.setStatus(ProjectStatus.DRAFT);
        project.setCreatedBy(creator);
        project = saveWithUniqueSlug(project, slugify(request.title()));

        return toResponse(project, List.of(), List.of(), creator.getUuid());
    }

    /** {@code STUDENT}/{@code TRAINER_PM}/{@code DEVELOPER} always see {@code PUBLISHED} only,
     * regardless of the {@code status} query param — only {@code ADMIN} can browse drafts/archived
     * rows through this endpoint. A {@code DEVELOPER} never needs to list their own draft here:
     * every other endpoint in this feature (update/assets/challenges/publish) operates on the id
     * they already hold from {@link #create}'s response. */
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> list(Long callerUserId, ProjectDifficulty difficulty, String domain,
            ProjectStatus status, Pageable pageable) {
        String callerUuid = requireUser(callerUserId).getUuid();
        ProjectStatus effectiveStatus = SecurityUtils.currentUserRoles().contains(RoleCode.ADMIN.name())
                ? status : ProjectStatus.PUBLISHED;

        Page<Project> page = projectRepository.search(difficulty, domain, effectiveStatus, pageable);
        List<Project> projects = page.getContent();
        if (projects.isEmpty()) {
            return PageResponse.from(page.map(p -> toResponse(p, List.of(), List.of(), callerUuid)));
        }

        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        Map<Long, List<ProjectAsset>> assetsByProject = projectAssetRepository
                .findByProjectIdInOrderBySortOrderAsc(projectIds).stream()
                .collect(Collectors.groupingBy(a -> a.getProject().getId()));
        Map<Long, List<BugChallenge>> challengesByProject = bugChallengeRepository.findByProjectIdIn(projectIds)
                .stream().collect(Collectors.groupingBy(c -> c.getProject().getId()));

        return PageResponse.from(page.map(p -> toResponse(p,
                assetsByProject.getOrDefault(p.getId(), List.of()),
                challengesByProject.getOrDefault(p.getId(), List.of()),
                callerUuid)));
    }

    /** Edits a still-{@code DRAFT} row in place; bumps a {@code PUBLISHED}/{@code ARCHIVED} one
     * into a brand-new {@code DRAFT} row instead — build-plan.md: "Version bump creates a new row;
     * previous archived, never overwritten." Every field null-preserves the existing value (same
     * convention {@code StandupService#update} established), applied identically whichever path
     * runs. */
    @Transactional
    public ProjectResponse update(Long callerUserId, Long projectId, UpdateProjectRequest request) {
        Project project = requireProject(projectId);
        User caller = requireCreatorOrAdmin(callerUserId, project);

        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.PROJECT_INVALID_TRANSITION,
                    "This project version is archived and superseded — it can no longer be edited.");
        }

        if (project.getStatus() == ProjectStatus.DRAFT) {
            applyUpdate(project, request);
            return toResponse(project,
                    projectAssetRepository.findByProjectIdOrderBySortOrderAsc(projectId),
                    bugChallengeRepository.findByProjectId(projectId), caller.getUuid());
        }

        if (request.version() == null || request.version().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A new version identifier is required to edit a published project.");
        }

        Project newVersion = new Project();
        newVersion.setTitle(project.getTitle());
        newVersion.setDescription(project.getDescription());
        newVersion.setTechStack(project.getTechStack());
        newVersion.setDifficulty(project.getDifficulty());
        newVersion.setDomain(project.getDomain());
        newVersion.setStarterRepoUrl(project.getStarterRepoUrl());
        newVersion.setVersion(project.getVersion());
        newVersion.setCreatedBy(project.getCreatedBy());
        newVersion.setStatus(ProjectStatus.DRAFT);
        applyUpdate(newVersion, request);
        newVersion = saveWithUniqueSlug(newVersion, slugify(project.getSlug() + "-" + request.version()));

        // PUBLISHED -> ARCHIVED only; a DRAFT never reaches this branch (handled above).
        project.setStatus(ProjectStatus.ARCHIVED);

        return toResponse(newVersion, List.of(), List.of(), caller.getUuid());
    }

    /** Exactly one of {@code file}/{@code externalUrl} — matches V8's {@code
     * chk_project_assets_source} CHECK. {@code file} goes through {@code StorageService#upload}'s
     * full untrusted-client validation (magic bytes + size, code-standards.md "File uploads") —
     * same treatment {@code ResumeService#upload} already gives a client-supplied file, and the
     * reason {@code VIDEO}/{@code DESIGN_FILE} assets (formats {@code FileSignatures} doesn't —
     * and given how open-ended video/design-file formats are, realistically can't — cover) are
     * only ever accepted as an {@code externalUrl} (e.g. a YouTube/Figma link) in practice; an
     * upload attempt for one of those types fails {@code UNSUPPORTED_FILE_TYPE} organically, no
     * extra asset-type-specific check needed here. The upload runs (and the key is computed)
     * *before* the row is ever saved — {@code chk_project_assets_source} requires exactly one of
     * {@code file_key}/{@code external_url} to be non-null on every row, including the first
     * insert, so there's no legal intermediate "row exists, key not yet known" state to save into.
     * The key embeds a random component, not the asset's own id, for exactly that reason — the id
     * doesn't exist until after the row is saved. */
    @Transactional
    public ProjectAssetResponse addAsset(Long callerUserId, Long projectId, AssetType assetType, String title,
            MultipartFile file, String externalUrl, Integer sortOrder) {
        Project project = requireProject(projectId);
        User caller = requireCreatorOrAdmin(callerUserId, project);
        requireNotArchived(project);
        boolean hasFile = file != null && !file.isEmpty();
        boolean hasExternalUrl = externalUrl != null && !externalUrl.isBlank();
        if (hasFile == hasExternalUrl) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Exactly one of file or externalUrl must be provided.");
        }
        // Validated before any upload happens — an invalid title/sortOrder caught only at the
        // final INSERT (a VARCHAR(200) truncation, a SMALLINT UNSIGNED range error) would leave
        // an orphaned object already sitting in storage with no row ever referencing it.
        if (title != null && title.length() > 200) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "title must be at most 200 characters.");
        }
        if (sortOrder != null && sortOrder < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "sortOrder cannot be negative.");
        }

        ProjectAsset asset = new ProjectAsset();
        asset.setProject(project);
        asset.setAssetType(assetType);
        asset.setTitle(title);
        asset.setSortOrder(sortOrder != null ? sortOrder : 0);

        if (hasFile) {
            String key = "projects/" + projectId + "/assets/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
            storageService.upload(key, readBytes(file), file.getContentType());
            asset.setFileKey(key);
        } else {
            asset.setExternalUrl(externalUrl);
        }
        projectAssetRepository.save(asset);

        return toAssetResponse(asset, caller.getUuid());
    }

    /** {@code brokenCodeKey} is required (V8: {@code broken_code_key NOT NULL}) — so, same
     * reasoning as {@link #addAsset}'s Javadoc, the upload runs (and the key is computed, with a
     * random component rather than the not-yet-existing row's id) *before* the row is ever saved;
     * there's no legal "row exists, {@code broken_code_key} still null" intermediate state for a
     * {@code NOT NULL} column either. {@code testScriptKey} optional. Both are arbitrary
     * source-code archives — not a format {@code FileSignatures} can magic-byte-sniff (plain
     * text/zip has no reliable signature the way a PDF or JPEG does) — so, unlike {@link
     * #addAsset}, these upload via {@code StorageService#uploadTrusted}: a {@code DEVELOPER}
     * authoring their own challenge content is a materially different trust boundary than a
     * public-facing upload, closer to the server-generated-content case {@code uploadTrusted}
     * already exists for than to a resume upload. The size ceiling ({@code
     * Constants#MAX_UPLOAD_BYTES}) is still enforced manually here, since {@code uploadTrusted}
     * skips {@code upload}'s own check along with the magic-byte one. */
    @Transactional
    public ChallengeResponse addChallenge(Long callerUserId, Long projectId, String title, MultipartFile brokenCode,
            MultipartFile testScript, String expectedBehaviour, ProjectDifficulty difficulty) {
        Project project = requireProject(projectId);
        User caller = requireCreatorOrAdmin(callerUserId, project);
        requireNotArchived(project);
        if (brokenCode == null || brokenCode.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "brokenCode is required.");
        }
        // Validated before any upload happens — same reasoning as #addAsset: a NOT NULL/length
        // failure at INSERT time, after the file already sits in storage, orphans that object.
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "title must be 1-200 characters.");
        }
        if (expectedBehaviour == null || expectedBehaviour.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "expectedBehaviour is required.");
        }

        String brokenCodeKey = uploadTrustedCode(
                "projects/" + projectId + "/challenges/" + UUID.randomUUID() + "-broken-" + brokenCode.getOriginalFilename(),
                brokenCode);
        String testScriptKey = (testScript != null && !testScript.isEmpty())
                ? uploadTrustedCode(
                        "projects/" + projectId + "/challenges/" + UUID.randomUUID() + "-test-" + testScript.getOriginalFilename(),
                        testScript)
                : null;

        BugChallenge challenge = new BugChallenge();
        challenge.setProject(project);
        challenge.setTitle(title);
        challenge.setExpectedBehaviour(expectedBehaviour);
        challenge.setDifficulty(difficulty);
        // The actual author of this challenge's content, not the project's own creator — an
        // ADMIN can author a challenge on someone else's project via the requireCreatorOrAdmin
        // bypass below, and misattributing that to the project owner would be a wrong audit trail.
        challenge.setCreatedBy(caller);
        challenge.setBrokenCodeKey(brokenCodeKey);
        challenge.setTestScriptKey(testScriptKey);
        bugChallengeRepository.save(challenge);

        return toChallengeResponse(challenge, caller.getUuid());
    }

    private String uploadTrustedCode(String key, MultipartFile file) {
        byte[] bytes = readBytes(file);
        if (bytes.length > Constants.MAX_UPLOAD_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        storageService.uploadTrusted(key, bytes, file.getContentType());
        return key;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("[project] failed to read uploaded file", e);
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED);
        }
    }

    /** Only {@code DRAFT -> PUBLISHED} — build-plan.md's state machine has no other legal entry
     * into {@code PUBLISHED}, so re-publishing an already-{@code PUBLISHED} project or resurrecting
     * an {@code ARCHIVED} one both reject as an invalid transition instead of silently no-op'ing. */
    @Transactional
    public ProjectResponse publish(Long callerUserId, Long projectId) {
        Project project = requireProject(projectId);
        User caller = requireCreatorOrAdmin(callerUserId, project);

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new BusinessException(ErrorCode.PROJECT_INVALID_TRANSITION);
        }
        project.setStatus(ProjectStatus.PUBLISHED);

        return toResponse(project,
                projectAssetRepository.findByProjectIdOrderBySortOrderAsc(projectId),
                bugChallengeRepository.findByProjectId(projectId), caller.getUuid());
    }

    /** Cross-package entry point for {@code sprint.TaskService#create} — build-plan.md feature 15
     * verify line: "A DRAFT cannot attach to a task." Same bare-{@code Long}-id boundary crossing
     * {@code BatchService}/{@code SprintService} already establish for {@code TaskService}. */
    @Transactional(readOnly = true)
    public void requirePublished(Long projectId) {
        Project project = requireProject(projectId);
        if (project.getStatus() != ProjectStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_PUBLISHED);
        }
    }

    private void applyUpdate(Project project, UpdateProjectRequest request) {
        if (request.title() != null) project.setTitle(request.title());
        if (request.description() != null) project.setDescription(request.description());
        if (request.techStack() != null) project.setTechStack(toJson(request.techStack()));
        if (request.difficulty() != null) project.setDifficulty(request.difficulty());
        if (request.domain() != null) project.setDomain(request.domain());
        if (request.starterRepoUrl() != null) project.setStarterRepoUrl(request.starterRepoUrl());
        if (request.version() != null) project.setVersion(request.version());
    }

    /** Returns the caller's own {@code User} — every call site needs the uuid a moment later to
     * presign the response's asset/challenge URLs (see {@code OwnershipGuard#canAccessProject},
     * which checks the *caller's* uuid/admin-role, not the project creator's), and {@link
     * #addChallenge} needs the full entity to record the actual author, not the project's owner —
     * resolving it here saves a second {@code requireUser} lookup at each call site either way. */
    private User requireCreatorOrAdmin(Long callerUserId, Project project) {
        User caller = requireUser(callerUserId);
        if (SecurityUtils.currentUserRoles().contains(RoleCode.ADMIN.name())) {
            return caller;
        }
        if (!project.getCreatedBy().getId().equals(callerUserId)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return caller;
    }

    /** {@code ARCHIVED} is a superseded, historical version — build-plan.md: "previous archived,
     * never overwritten." That guarantee only holds if nothing can still mutate an archived row
     * after the fact by attaching a new asset or challenge to it. */
    private void requireNotArchived(Project project) {
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.PROJECT_INVALID_TRANSITION,
                    "This project version is archived and superseded — it can no longer be modified.");
        }
    }

    /** Lowercase, non-alphanumerics collapsed to a single {@code -}. Pure string transform, no DB
     * read — uniqueness is resolved by {@link #saveWithUniqueSlug}'s insert-and-retry instead of a
     * check-then-insert race (two concurrent creates with the same title could both pass an
     * {@code existsBySlug} check before either commits; confirmed reachable and fixed via
     * {@code /review}). */
    private String slugify(String source) {
        String base = source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return base.isBlank() ? "project" : base;
    }

    /** {@code ProjectWriter}, not {@code projectRepository} directly — see its own Javadoc. On a
     * {@code uq_projects_slug} collision, retries with an incrementing numeric suffix; each retry
     * is a fresh {@code REQUIRES_NEW} attempt since the previous one's session is poisoned the
     * instant its flush fails. */
    private Project saveWithUniqueSlug(Project project, String baseSlug) {
        String candidate = baseSlug;
        for (int attempt = 2; attempt <= Constants.MAX_SLUG_GENERATION_ATTEMPTS + 1; attempt++) {
            project.setSlug(candidate);
            try {
                return projectWriter.trySave(project);
            } catch (DataIntegrityViolationException | CannotAcquireLockException e) {
                candidate = baseSlug + "-" + attempt;
            }
        }
        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Could not generate a unique slug for this project.");
    }

    private ProjectAssetResponse toAssetResponse(ProjectAsset asset, String callerUuid) {
        // fileKey is already the full storage key (set in #addAsset) — not just a filename.
        String url = asset.getFileKey() != null
                ? presignedUrl(asset.getFileKey(), callerUuid)
                : asset.getExternalUrl();
        return new ProjectAssetResponse(asset.getId(), asset.getAssetType(), asset.getTitle(), url, asset.getSortOrder());
    }

    private ChallengeResponse toChallengeResponse(BugChallenge challenge, String callerUuid) {
        return new ChallengeResponse(
                challenge.getId(),
                challenge.getTitle(),
                challenge.getExpectedBehaviour(),
                presignedUrl(challenge.getBrokenCodeKey(), callerUuid),
                challenge.getTestScriptKey() != null ? presignedUrl(challenge.getTestScriptKey(), callerUuid) : null,
                challenge.getDifficulty());
    }

    /** {@code callerUuid} — MANDATORY, threaded all the way from each public method's own caller
     * (see {@code StorageService#presignedGetUrl}'s Javadoc: "signing a key because the caller
     * asked for it is an IDOR"). {@code OwnershipGuard} decides per-key whether *this* caller may
     * see it — a published project's assets/challenges presign for anyone; a draft's presign only
     * for its creator or an {@code ADMIN} (see {@code OwnershipGuard#canAccessProject}). Deliberately
     * does not catch-and-degrade a denial to a null {@code url}: every row this service itself ever
     * writes has a well-formed, presignable key, so a failure here means either a genuine
     * authorization problem or a data-integrity issue upstream — both worth surfacing loudly, not
     * masking as an empty field a caller might not notice (`/review` — see progress-tracker.md for
     * the {@code LearningSchemaIT} test-fixture bug this caught during development). */
    private String presignedUrl(String key, String callerUuid) {
        URL url = storageService.presignedGetUrl(callerUuid, key, PRESIGN_TTL);
        return url.toString();
    }

    private ProjectResponse toResponse(Project project, List<ProjectAsset> assets, List<BugChallenge> challenges,
            String callerUuid) {
        User creator = project.getCreatedBy();
        return new ProjectResponse(
                project.getId(),
                project.getTitle(),
                project.getSlug(),
                project.getDescription(),
                fromJsonList(project.getTechStack()),
                project.getDifficulty(),
                project.getDomain(),
                project.getStarterRepoUrl(),
                project.getVersion(),
                project.getStatus(),
                creator.getUuid(),
                creator.getFullName(),
                assets.stream().map(a -> toAssetResponse(a, callerUuid)).toList(),
                challenges.stream().map(c -> toChallengeResponse(c, callerUuid)).toList());
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PROJECT_NOT_FOUND, projectId));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("[project] failed to serialize techStack, storing null", e);
            return null;
        }
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            CollectionType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, String.class);
            return objectMapper.readValue(json, listType);
        } catch (JsonProcessingException e) {
            log.warn("[project] failed to parse stored techStack JSON, treating as empty", e);
            return Collections.emptyList();
        }
    }
}
