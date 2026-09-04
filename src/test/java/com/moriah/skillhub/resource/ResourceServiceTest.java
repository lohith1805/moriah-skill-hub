package com.moriah.skillhub.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.AuthenticatedPrincipal;
import com.moriah.skillhub.resource.dto.CreateResourceRequest;
import com.moriah.skillhub.resource.dto.LearningResourceResponse;
import com.moriah.skillhub.resource.dto.UpdateResourceRequest;
import com.moriah.skillhub.resource.entity.LearningResource;
import com.moriah.skillhub.resource.entity.ResourceCategory;
import com.moriah.skillhub.resource.repository.LearningResourceRepository;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock
    private LearningResourceRepository resourceRepository;
    @Mock
    private UserRepository userRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ResourceService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId, String... roles) {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, "uuid-" + userId, List.of(roles));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
    }

    private LearningResource resource(long id, long createdBy) {
        LearningResource r = new LearningResource();
        r.setId(id);
        r.setTitle("Clean Architecture");
        r.setCategory(ResourceCategory.ARTICLE);
        r.setUrl("https://example.com/clean-arch");
        r.setTags("[\"architecture\",\"design\"]");
        r.setCreatedBy(createdBy);
        r.setActive(true);
        return r;
    }

    @Test
    void create_persistsWithCallerAsCreator_andSerializesTags() {
        User creator = new User();
        creator.setId(7L);
        creator.setUuid("uuid-7");
        when(resourceRepository.save(any(LearningResource.class))).thenAnswer(inv -> {
            LearningResource r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(userRepository.findAllById(List.of(7L))).thenReturn(List.of(creator));

        LearningResourceResponse response = service.create(
                new CreateResourceRequest("Clean Architecture", "  ", ResourceCategory.ARTICLE,
                        "https://example.com/clean-arch", List.of("architecture", "architecture", "design"), "FULL_STACK"),
                7L);

        ArgumentCaptor<LearningResource> captor = ArgumentCaptor.forClass(LearningResource.class);
        verify(resourceRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(7L);
        assertThat(captor.getValue().getDescription()).isNull();
        assertThat(captor.getValue().getTags()).isEqualTo("[\"architecture\",\"design\"]");
        assertThat(response.tags()).containsExactly("architecture", "design");
        assertThat(response.createdByUuid()).isEqualTo("uuid-7");
    }

    @Test
    void list_nonCurator_neverSeesInactiveEvenWhenAsked() {
        authenticateAs(50L, RoleCode.STUDENT.name());
        when(resourceRepository.search(eq(true), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.list(true, null, null, null, PageRequest.of(0, 20));

        // activeOnly stays true (first arg) despite includeInactive=true, because the caller is not a curator
        verify(resourceRepository).search(eq(true), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void list_curator_canIncludeInactive() {
        authenticateAs(9L, RoleCode.ADMIN.name());
        when(resourceRepository.search(eq(false), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resource(1L, 9L)), PageRequest.of(0, 20), 1));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        PageResponse<LearningResourceResponse> page = service.list(true, null, null, null, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        verify(resourceRepository).search(eq(false), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void update_byNonCreatorNonAdmin_isForbidden() {
        authenticateAs(2L, RoleCode.DEVELOPER.name());
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource(1L, 999L)));

        assertThatThrownBy(() -> service.update(1L,
                new UpdateResourceRequest("t", null, ResourceCategory.VIDEO, "https://x", List.of(), true, null), 2L))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void update_byAdmin_onSomeoneElsesResource_isAllowed() {
        authenticateAs(2L, RoleCode.ADMIN.name());
        LearningResource r = resource(1L, 999L);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(r));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        service.update(1L, new UpdateResourceRequest("Renamed", "d", ResourceCategory.COURSE,
                "https://new", List.of("x"), false, "PRODUCT_DESIGN"), 2L);

        assertThat(r.getTitle()).isEqualTo("Renamed");
        assertThat(r.getCategory()).isEqualTo(ResourceCategory.COURSE);
        assertThat(r.getTrack()).isEqualTo("PRODUCT_DESIGN");
        assertThat(r.isActive()).isFalse();
    }

    @Test
    void deactivate_byCreator_flipsActive() {
        authenticateAs(7L, RoleCode.TRAINER_PM.name());
        LearningResource r = resource(1L, 7L);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(r));

        service.deactivate(1L, 7L);

        assertThat(r.isActive()).isFalse();
    }

    @Test
    void get_unknownId_throwsNotFound() {
        when(resourceRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
