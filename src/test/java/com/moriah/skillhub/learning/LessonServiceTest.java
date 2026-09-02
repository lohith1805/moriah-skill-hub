package com.moriah.skillhub.learning;

import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.AuthenticatedPrincipal;
import com.moriah.skillhub.learning.dto.CreateVideoLessonRequest;
import com.moriah.skillhub.learning.dto.LessonProgressView;
import com.moriah.skillhub.learning.dto.MyLessonProgressItem;
import com.moriah.skillhub.learning.dto.RecordLessonProgressRequest;
import com.moriah.skillhub.learning.dto.UpdateVideoLessonRequest;
import com.moriah.skillhub.learning.dto.VideoLessonResponse;
import com.moriah.skillhub.learning.entity.LessonProgress;
import com.moriah.skillhub.learning.entity.LessonProgressStatus;
import com.moriah.skillhub.learning.entity.VideoLesson;
import com.moriah.skillhub.learning.repository.LessonProgressRepository;
import com.moriah.skillhub.learning.repository.VideoLessonRepository;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
class LessonServiceTest {

    @Mock
    private VideoLessonRepository lessonRepository;
    @Mock
    private LessonProgressRepository progressRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LessonService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId, String... roles) {
        AuthenticatedPrincipal p = new AuthenticatedPrincipal(userId, "uuid-" + userId, List.of(roles));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(p, null));
    }

    private VideoLesson lesson(long id, long createdBy, boolean published) {
        VideoLesson l = new VideoLesson();
        l.setId(id);
        l.setTitle("Intro to Spring");
        l.setModuleName("Backend Basics");
        l.setVideoUrl("https://videos.example/1");
        l.setSortOrder(1);
        l.setCreatedBy(createdBy);
        l.setPublished(published);
        return l;
    }

    @Test
    void create_setsCallerAsCreator() {
        when(lessonRepository.save(any(VideoLesson.class))).thenAnswer(inv -> {
            VideoLesson l = inv.getArgument(0);
            l.setId(1L);
            return l;
        });
        User u = new User();
        u.setId(7L);
        u.setUuid("uuid-7");
        when(userRepository.findAllById(List.of(7L))).thenReturn(List.of(u));

        VideoLessonResponse response = service.create(new CreateVideoLessonRequest(
                "Intro to Spring", "  ", "Backend Basics", "https://videos.example/1", 600, 1, false), 7L);

        ArgumentCaptor<VideoLesson> captor = ArgumentCaptor.forClass(VideoLesson.class);
        verify(lessonRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(7L);
        assertThat(captor.getValue().getDescription()).isNull();
        assertThat(captor.getValue().isPublished()).isFalse();
        assertThat(response.progress().status()).isEqualTo(LessonProgressView.NOT_STARTED);
        assertThat(response.createdByUuid()).isEqualTo("uuid-7");
    }

    @Test
    void list_embedsCallerProgressPerLesson_withoutNPlusOne() {
        authenticateAs(50L, RoleCode.STUDENT.name());
        VideoLesson a = lesson(1L, 9L, true);
        VideoLesson b = lesson(2L, 9L, true);
        when(lessonRepository.search(eq(true), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b), PageRequest.of(0, 20), 2));
        LessonProgress p = new LessonProgress();
        p.setLessonId(1L);
        p.setUserId(50L);
        p.setStatus(LessonProgressStatus.COMPLETED);
        p.setWatchedSeconds(600);
        when(progressRepository.findByUserIdAndLessonIdIn(50L, List.of(1L, 2L))).thenReturn(List.of(p));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        PageResponse<VideoLessonResponse> page = service.list(false, null, 50L, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(2);
        assertThat(page.content().get(0).progress().status()).isEqualTo("COMPLETED");
        assertThat(page.content().get(1).progress().status()).isEqualTo(LessonProgressView.NOT_STARTED);
        verify(progressRepository).findByUserIdAndLessonIdIn(50L, List.of(1L, 2L));
    }

    @Test
    void list_nonCurator_cannotSeeUnpublished() {
        authenticateAs(50L, RoleCode.STUDENT.name());
        when(lessonRepository.search(eq(true), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.list(true, null, 50L, PageRequest.of(0, 20));

        verify(lessonRepository).search(eq(true), any(), any(Pageable.class));
    }

    @Test
    void list_curator_canSeeUnpublished() {
        authenticateAs(9L, RoleCode.DEVELOPER.name());
        when(lessonRepository.search(eq(false), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.list(true, null, 9L, PageRequest.of(0, 20));

        verify(lessonRepository).search(eq(false), any(), any(Pageable.class));
    }

    @Test
    void update_byNonCreatorNonAdmin_isForbidden() {
        authenticateAs(2L, RoleCode.DEVELOPER.name());
        when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson(1L, 999L, true)));

        assertThatThrownBy(() -> service.update(1L, new UpdateVideoLessonRequest(
                "t", null, "m", "https://x", null, 0, true), 2L))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void update_byAdmin_onAnotherPersonsLesson_isAllowed() {
        authenticateAs(2L, RoleCode.ADMIN.name());
        VideoLesson l = lesson(1L, 999L, false);
        when(lessonRepository.findById(1L)).thenReturn(Optional.of(l));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        service.update(1L, new UpdateVideoLessonRequest(
                "Renamed", "d", "New Module", "https://new", 900, 3, true), 2L);

        assertThat(l.getTitle()).isEqualTo("Renamed");
        assertThat(l.getModuleName()).isEqualTo("New Module");
        assertThat(l.isPublished()).isTrue();
    }

    @Test
    void get_unknownId_throwsNotFound() {
        when(lessonRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L, 1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void recordProgress_firstReport_createsRowInProgress() {
        VideoLesson l = lesson(1L, 9L, true);
        when(lessonRepository.findById(1L)).thenReturn(Optional.of(l));
        when(progressRepository.findByLessonIdAndUserId(1L, 50L)).thenReturn(Optional.empty());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        VideoLessonResponse response = service.recordProgress(1L, new RecordLessonProgressRequest(120, false), 50L);

        ArgumentCaptor<LessonProgress> captor = ArgumentCaptor.forClass(LessonProgress.class);
        verify(progressRepository).save(captor.capture());
        assertThat(captor.getValue().getLessonId()).isEqualTo(1L);
        assertThat(captor.getValue().getUserId()).isEqualTo(50L);
        assertThat(captor.getValue().getStatus()).isEqualTo(LessonProgressStatus.IN_PROGRESS);
        assertThat(captor.getValue().getWatchedSeconds()).isEqualTo(120);
        assertThat(response.progress().status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void recordProgress_neverRewindsWatchedSeconds_andCompletesOnce() {
        VideoLesson l = lesson(1L, 9L, true);
        LessonProgress existing = new LessonProgress();
        existing.setLessonId(1L);
        existing.setUserId(50L);
        existing.setStatus(LessonProgressStatus.IN_PROGRESS);
        existing.setWatchedSeconds(300);
        when(lessonRepository.findById(1L)).thenReturn(Optional.of(l));
        when(progressRepository.findByLessonIdAndUserId(1L, 50L)).thenReturn(Optional.of(existing));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        service.recordProgress(1L, new RecordLessonProgressRequest(90, true), 50L);

        assertThat(existing.getWatchedSeconds()).isEqualTo(300); // not lowered to 90
        assertThat(existing.getStatus()).isEqualTo(LessonProgressStatus.COMPLETED);
        assertThat(existing.getCompletedAt()).isNotNull();

        java.time.Instant firstCompletion = existing.getCompletedAt();
        service.recordProgress(1L, new RecordLessonProgressRequest(310, true), 50L);
        assertThat(existing.getCompletedAt()).isEqualTo(firstCompletion); // stamped once
        assertThat(existing.getWatchedSeconds()).isEqualTo(310);
    }

    @Test
    void recordProgress_onUnpublishedLesson_forLearner_is404() {
        authenticateAs(50L, RoleCode.STUDENT.name());
        VideoLesson l = lesson(1L, 9L, false);
        when(lessonRepository.findById(1L)).thenReturn(Optional.of(l));

        assertThatThrownBy(() -> service.recordProgress(1L, new RecordLessonProgressRequest(10, false), 50L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unpublish_byCreator_flipsPublishedFalse() {
        authenticateAs(9L, RoleCode.DEVELOPER.name());
        VideoLesson l = lesson(1L, 9L, true);
        when(lessonRepository.findById(1L)).thenReturn(Optional.of(l));

        service.unpublish(1L, 9L);

        assertThat(l.isPublished()).isFalse();
    }

    @Test
    void myProgress_joinsLessonTitleOntoEachRow() {
        LessonProgress p = new LessonProgress();
        p.setLessonId(1L);
        p.setUserId(50L);
        p.setStatus(LessonProgressStatus.COMPLETED);
        p.setWatchedSeconds(600);
        when(progressRepository.findByUserId(eq(50L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1));
        VideoLesson l = lesson(1L, 9L, true);
        l.setDurationSeconds(600);
        when(lessonRepository.findAllById(List.of(1L))).thenReturn(List.of(l));

        PageResponse<MyLessonProgressItem> page = service.myProgress(50L, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        MyLessonProgressItem item = page.content().get(0);
        assertThat(item.title()).isEqualTo("Intro to Spring");
        assertThat(item.moduleName()).isEqualTo("Backend Basics");
        assertThat(item.status()).isEqualTo("COMPLETED");
        assertThat(item.durationSeconds()).isEqualTo(600);
    }
}
