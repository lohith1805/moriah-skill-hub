package com.moriah.skillhub.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.project.dto.ChallengeResponse;
import com.moriah.skillhub.project.dto.ProjectResponse;
import com.moriah.skillhub.project.dto.UpdateProjectRequest;
import com.moriah.skillhub.project.entity.AssetType;
import com.moriah.skillhub.project.entity.BugChallenge;
import com.moriah.skillhub.project.entity.Project;
import com.moriah.skillhub.project.entity.ProjectStatus;
import com.moriah.skillhub.project.repository.BugChallengeRepository;
import com.moriah.skillhub.project.repository.ProjectAssetRepository;
import com.moriah.skillhub.project.repository.ProjectRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import com.moriah.skillhub.common.security.AuthenticatedPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.URL;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code StorageService} is mocked entirely — its own upload/presign logic has its own coverage
 * (feature 08); this class only proves {@code ProjectService} calls it correctly and with the
 * right key shape. {@code ObjectMapper} is real (no mocking needed for simple {@code
 * List<String>} techStack serialization). {@code ProjectWriter} is mocked too (package-private,
 * same package) — its own {@code REQUIRES_NEW} isolation only matters against a real DB, not a
 * unit test. */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectAssetRepository projectAssetRepository;
    @Mock
    private BugChallengeRepository bugChallengeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private ProjectWriter projectWriter;

    private ProjectService projectService;

    private User developer;
    private User admin;
    private User bystander;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, projectAssetRepository, bugChallengeRepository,
                userRepository, storageService, new ObjectMapper(), projectWriter);

        developer = new User();
        developer.setId(1L);
        developer.setUuid("dev-uuid");
        developer.setFullName("Dev One");

        admin = new User();
        admin.setId(2L);
        admin.setUuid("admin-uuid");

        bystander = new User();
        bystander.setId(3L);
        bystander.setUuid("bystander-uuid");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId, List<String> roles) {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, "uuid-" + userId, roles);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
    }

    private Project project(long id, ProjectStatus status, String slug) {
        Project project = new Project();
        project.setId(id);
        project.setTitle("Weather App");
        project.setSlug(slug);
        project.setStatus(status);
        project.setCreatedBy(developer);
        return project;
    }

    private void stubTrySaveSucceeds() {
        when(projectWriter.trySave(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_happyPath_savesDraftProjectWithGeneratedSlug() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));
        stubTrySaveSucceeds();

        ProjectResponse response = projectService.create(1L, createRequest("Weather App"));

        assertThat(response.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(response.slug()).isEqualTo("weather-app");
        assertThat(response.createdByUuid()).isEqualTo("dev-uuid");
    }

    /** Regression test for the TOCTOU race `/review` found: two concurrent creates with the same
     * title could both pass a check-then-insert `existsBySlug` probe before either committed.
     * {@code saveWithUniqueSlug} instead lets the unique constraint itself be authoritative,
     * retrying with an incrementing suffix on {@link DataIntegrityViolationException}. */
    @Test
    void create_slugCollision_retriesWithNumericSuffixOnConstraintViolation() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));
        when(projectWriter.trySave(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate slug"))
                .thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse response = projectService.create(1L, createRequest("Weather App"));

        assertThat(response.slug()).isEqualTo("weather-app-2");
    }

    @Test
    void update_byBystander_throwsForbidden() {
        Project project = project(10L, ProjectStatus.DRAFT, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(3L)).thenReturn(Optional.of(bystander));

        assertThatThrownBy(() -> projectService.update(3L, 10L, updateRequest()))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    void update_archivedProject_throwsInvalidTransition() {
        Project project = project(10L, ProjectStatus.ARCHIVED, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));

        assertThatThrownBy(() -> projectService.update(1L, 10L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROJECT_INVALID_TRANSITION);
    }

    @Test
    void update_draftProject_editsInPlaceNullPreservingOmittedFields() {
        Project project = project(10L, ProjectStatus.DRAFT, "weather-app");
        project.setDescription("original description");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));
        when(projectAssetRepository.findByProjectIdOrderBySortOrderAsc(10L)).thenReturn(List.of());
        when(bugChallengeRepository.findByProjectId(10L)).thenReturn(List.of());

        UpdateProjectRequest request = new UpdateProjectRequest("New Title", null, null, null, null, null, null);
        ProjectResponse response = projectService.update(1L, 10L, request);

        assertThat(response.title()).isEqualTo("New Title");
        assertThat(response.description()).isEqualTo("original description");
        assertThat(response.id()).isEqualTo(10L);
        verify(projectWriter, never()).trySave(any());
    }

    @Test
    void update_publishedProjectWithoutVersion_throwsValidation() {
        Project project = project(10L, ProjectStatus.PUBLISHED, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));

        assertThatThrownBy(() -> projectService.update(1L, 10L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void update_publishedProjectWithVersion_createsNewDraftAndArchivesOld() {
        Project project = project(10L, ProjectStatus.PUBLISHED, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));
        stubTrySaveSucceeds();

        UpdateProjectRequest request = new UpdateProjectRequest(null, null, null, null, null, null, "v2");
        ProjectResponse response = projectService.update(1L, 10L, request);

        assertThat(response.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(response.slug()).isEqualTo("weather-app-v2");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        verify(projectWriter).trySave(any(Project.class));
    }

    @Test
    void addAsset_neitherFileNorUrl_throwsValidation() {
        Project project = project(10L, ProjectStatus.DRAFT, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));

        assertThatThrownBy(() -> projectService.addAsset(1L, 10L, AssetType.IMAGE, "t", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void addAsset_bothFileAndUrl_throwsValidation() {
        Project project = project(10L, ProjectStatus.DRAFT, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));
        MockMultipartFile file = new MockMultipartFile("file", "shot.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> projectService.addAsset(1L, 10L, AssetType.IMAGE, "t", file, "https://x.com/a.png", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void addAsset_titleTooLong_throwsValidationBeforeUpload() {
        Project project = project(10L, ProjectStatus.DRAFT, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));
        MockMultipartFile file = new MockMultipartFile("file", "shot.png", "image/png", new byte[]{1});
        String tooLong = "x".repeat(201);

        assertThatThrownBy(() -> projectService.addAsset(1L, 10L, AssetType.IMAGE, tooLong, file, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        verify(storageService, never()).upload(any(), any(), any());
    }

    @Test
    void addAsset_negativeSortOrder_throwsValidationBeforeUpload() {
        Project project = project(10L, ProjectStatus.DRAFT, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));
        MockMultipartFile file = new MockMultipartFile("file", "shot.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> projectService.addAsset(1L, 10L, AssetType.IMAGE, "t", file, null, -1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        verify(storageService, never()).upload(any(), any(), any());
    }

    @Test
    void addAsset_archivedProject_throwsInvalidTransition() {
        Project project = project(10L, ProjectStatus.ARCHIVED, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));

        assertThatThrownBy(() -> projectService.addAsset(1L, 10L, AssetType.VIDEO, "t", null, "https://x.com/a", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROJECT_INVALID_TRANSITION);
    }

    @Test
    void addAsset_externalUrl_savesWithoutUploadingAndPresignsNothing() {
        Project project = project(10L, ProjectStatus.DRAFT, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));

        var response = projectService.addAsset(1L, 10L, AssetType.VIDEO, "Demo", null, "https://youtu.be/x", 1);

        assertThat(response.url()).isEqualTo("https://youtu.be/x");
        verify(storageService, never()).upload(any(), any(), any());
        verify(storageService, never()).presignedGetUrl(any(), any(), any());
    }

    @Test
    void addAsset_file_uploadsWithProjectScopedKeyAndPresignsForCaller() throws Exception {
        Project project = project(10L, ProjectStatus.DRAFT, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));
        when(storageService.presignedGetUrl(eq("dev-uuid"), anyString(), any())).thenReturn(new URL("https://s3/x"));
        MockMultipartFile file = new MockMultipartFile("file", "shot.png", "image/png", new byte[]{1, 2, 3});

        var response = projectService.addAsset(1L, 10L, AssetType.IMAGE, "Shot", file, null, null);

        assertThat(response.url()).isEqualTo("https://s3/x");
        verify(storageService).upload(argThatKeyStartsWith("projects/10/assets/"), any(), eq("image/png"));
    }

    @Test
    void addChallenge_missingBrokenCode_throwsValidation() {
        Project project = project(10L, ProjectStatus.DRAFT, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));

        assertThatThrownBy(() -> projectService.addChallenge(1L, 10L, "Off by one", null, null, "Loop should stop at n", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void addChallenge_blankTitle_throwsValidationBeforeUpload() {
        Project project = project(10L, ProjectStatus.DRAFT, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));
        MockMultipartFile brokenCode = new MockMultipartFile("brokenCode", "app.zip", "application/zip", new byte[]{1});

        assertThatThrownBy(() -> projectService.addChallenge(1L, 10L, "  ", brokenCode, null, "Loop should stop at n", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        verify(storageService, never()).uploadTrusted(any(), any(), any());
    }

    @Test
    void addChallenge_archivedProject_throwsInvalidTransition() {
        Project project = project(10L, ProjectStatus.ARCHIVED, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));
        MockMultipartFile brokenCode = new MockMultipartFile("brokenCode", "app.zip", "application/zip", new byte[]{1});

        assertThatThrownBy(() -> projectService.addChallenge(1L, 10L, "Off by one", brokenCode, null, "desc", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROJECT_INVALID_TRANSITION);
    }

    @Test
    void addChallenge_happyPath_uploadsTrustedAndPresigns() throws Exception {
        Project project = project(10L, ProjectStatus.DRAFT, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));
        when(storageService.presignedGetUrl(eq("dev-uuid"), anyString(), any())).thenReturn(new URL("https://s3/x"));
        MockMultipartFile brokenCode = new MockMultipartFile("brokenCode", "app.zip", "application/zip", new byte[]{1});

        var response = projectService.addChallenge(1L, 10L, "Off by one", brokenCode, null, "Loop should stop at n", null);

        assertThat(response.brokenCodeUrl()).isEqualTo("https://s3/x");
        assertThat(response.testScriptUrl()).isNull();
        verify(storageService).uploadTrusted(argThatKeyStartsWith("projects/10/challenges/"), any(), eq("application/zip"));
        verify(storageService, never()).upload(any(), any(), any());
    }

    /** Regression test for the misattribution `/review` found: an {@code ADMIN} acting on a
     * project they didn't create must be recorded as the challenge's actual author, not silently
     * attributed to the project's original {@code DEVELOPER} owner. */
    @Test
    void addChallenge_byAdminOnSomeoneElsesProject_attributesToTheAdminNotTheOwner() throws Exception {
        Project project = project(10L, ProjectStatus.DRAFT, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        authenticateAs(2L, List.of("ADMIN"));
        when(storageService.presignedGetUrl(eq("admin-uuid"), anyString(), any())).thenReturn(new URL("https://s3/x"));
        MockMultipartFile brokenCode = new MockMultipartFile("brokenCode", "app.zip", "application/zip", new byte[]{1});

        ArgumentCaptor<BugChallenge> captor = ArgumentCaptor.forClass(BugChallenge.class);
        ChallengeResponse response = projectService.addChallenge(2L, 10L, "Off by one", brokenCode, null, "desc", null);

        verify(bugChallengeRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(admin);
        assertThat(response).isNotNull();
    }

    @Test
    void publish_draftProject_transitionsToPublished() {
        Project project = project(10L, ProjectStatus.DRAFT, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));
        when(projectAssetRepository.findByProjectIdOrderBySortOrderAsc(10L)).thenReturn(List.of());
        when(bugChallengeRepository.findByProjectId(10L)).thenReturn(List.of());

        ProjectResponse response = projectService.publish(1L, 10L);

        assertThat(response.status()).isEqualTo(ProjectStatus.PUBLISHED);
    }

    @Test
    void publish_alreadyPublished_throwsInvalidTransition() {
        Project project = project(10L, ProjectStatus.PUBLISHED, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));

        assertThatThrownBy(() -> projectService.publish(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROJECT_INVALID_TRANSITION);
    }

    @Test
    void requirePublished_publishedProject_doesNotThrow() {
        Project project = project(10L, ProjectStatus.PUBLISHED, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));

        projectService.requirePublished(10L);
    }

    @Test
    void requirePublished_draftProject_throwsProjectNotPublished() {
        Project project = project(10L, ProjectStatus.DRAFT, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.requirePublished(10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROJECT_NOT_PUBLISHED);
    }

    @Test
    void requirePublished_nonexistentProject_throwsNotFound() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.requirePublished(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private BugChallenge challenge(long id, Project project) {
        BugChallenge c = new BugChallenge();
        c.setId(id);
        c.setProject(project);
        c.setTitle("Off by one");
        c.setExpectedBehaviour("Loop should stop at n");
        c.setBrokenCodeKey("projects/10/challenges/abc-broken-app.zip");
        c.setCreatedBy(developer);
        return c;
    }

    @Test
    void listChallenges_returnsEveryChallengeOnTheProject() throws Exception {
        Project project = project(10L, ProjectStatus.PUBLISHED, "weather-app");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));
        when(bugChallengeRepository.findByProjectId(10L)).thenReturn(List.of(challenge(1L, project), challenge(2L, project)));
        when(storageService.presignedGetUrl(eq("dev-uuid"), anyString(), any())).thenReturn(new URL("https://s3/x"));

        List<ChallengeResponse> result = projectService.listChallenges(1L, 10L);

        assertThat(result).hasSize(2);
    }

    @Test
    void getChallenge_unknownId_throwsNotFound() {
        when(bugChallengeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getChallenge(1L, 404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUG_CHALLENGE_NOT_FOUND);
    }

    @Test
    void updateChallenge_byCreator_replacesTextFields() throws Exception {
        Project project = project(10L, ProjectStatus.PUBLISHED, "weather-app");
        BugChallenge c = challenge(5L, project);
        when(bugChallengeRepository.findById(5L)).thenReturn(Optional.of(c));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));
        when(storageService.presignedGetUrl(eq("dev-uuid"), anyString(), any())).thenReturn(new URL("https://s3/x"));

        projectService.updateChallenge(1L, 5L, "Fencepost bug",
                "Loop must be < n, not <= n", com.moriah.skillhub.project.entity.ProjectDifficulty.ADVANCED);

        assertThat(c.getTitle()).isEqualTo("Fencepost bug");
        assertThat(c.getExpectedBehaviour()).isEqualTo("Loop must be < n, not <= n");
        assertThat(c.getDifficulty()).isEqualTo(com.moriah.skillhub.project.entity.ProjectDifficulty.ADVANCED);
    }

    @Test
    void updateChallenge_byBystander_isForbidden() {
        Project project = project(10L, ProjectStatus.PUBLISHED, "weather-app");
        BugChallenge c = challenge(5L, project);
        when(bugChallengeRepository.findById(5L)).thenReturn(Optional.of(c));
        when(userRepository.findById(3L)).thenReturn(Optional.of(bystander));

        assertThatThrownBy(() -> projectService.updateChallenge(3L, 5L, "x", "y", null))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void updateChallenge_onArchivedProject_throwsInvalidTransition() {
        Project project = project(10L, ProjectStatus.ARCHIVED, "weather-app");
        BugChallenge c = challenge(5L, project);
        when(bugChallengeRepository.findById(5L)).thenReturn(Optional.of(c));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));

        assertThatThrownBy(() -> projectService.updateChallenge(1L, 5L, "x", "y", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROJECT_INVALID_TRANSITION);
    }

    @Test
    void deleteChallenge_byCreator_rowDeletes() {
        Project project = project(10L, ProjectStatus.PUBLISHED, "weather-app");
        BugChallenge c = challenge(5L, project);
        when(bugChallengeRepository.findById(5L)).thenReturn(Optional.of(c));
        when(userRepository.findById(1L)).thenReturn(Optional.of(developer));

        projectService.deleteChallenge(1L, 5L);

        verify(bugChallengeRepository).delete(c);
    }

    private com.moriah.skillhub.project.dto.CreateProjectRequest createRequest(String title) {
        return new com.moriah.skillhub.project.dto.CreateProjectRequest(
                title, "A weather app", List.of("React", "Node"), null, "web", null, null);
    }

    private UpdateProjectRequest updateRequest() {
        return new UpdateProjectRequest(null, null, null, null, null, null, null);
    }

    private String argThatKeyStartsWith(String prefix) {
        return org.mockito.ArgumentMatchers.argThat(key -> key != null && key.startsWith(prefix));
    }
}
