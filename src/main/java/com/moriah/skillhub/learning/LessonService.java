package com.moriah.skillhub.learning;

import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.learning.dto.CreateVideoLessonRequest;
import com.moriah.skillhub.learning.dto.LessonProgressView;
import com.moriah.skillhub.learning.dto.ModuleSummaryResponse;
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
 * Video Lessons / self-paced learning (gap B1.4). Curation ({@code create}/{@code update}/{@code
 * unpublish}) is staff-only (gated on the controller); {@code update}/{@code unpublish}
 * additionally require creator-or-ADMIN, the same {@code SecurityUtils.currentUserRoles} check
 * {@code ProjectService}/{@code ResourceService} use. {@code UserRepository} is injected directly
 * to resolve creator uuids (shared identity primitive).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LessonService {

    private static final Set<String> CURATOR_ROLES = Set.of(
            RoleCode.DEVELOPER.name(), RoleCode.TRAINER_PM.name(), RoleCode.ADMIN.name());

    private final VideoLessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final com.moriah.skillhub.learning.repository.LessonQuizQuestionRepository quizQuestionRepository;
    private final com.moriah.skillhub.learning.repository.LessonQuizAttemptRepository quizAttemptRepository;
    private final UserRepository userRepository;

    /**
     * {@code track}, when supplied, narrows the list to lessons for that cohort track plus the
     * untracked (all-track) ones — a UX convenience the student view passes from their batch's
     * track, not an access boundary (published lessons were always readable by any authenticated
     * user). A curator omits it to author across every track.
     */
    @Transactional(readOnly = true)
    public PageResponse<VideoLessonResponse> list(boolean includeUnpublished, String module, String track,
                                                   Long callerUserId, Pageable pageable) {
        boolean publishedOnly = !(includeUnpublished && isCurator());
        Page<VideoLesson> page = lessonRepository.search(publishedOnly, blankToNull(module), blankToNull(track), pageable);

        List<Long> lessonIds = page.getContent().stream().map(VideoLesson::getId).toList();
        Map<Long, LessonProgress> progressByLesson = lessonIds.isEmpty()
                ? Map.of()
                : progressRepository.findByUserIdAndLessonIdIn(callerUserId, lessonIds).stream()
                        .collect(Collectors.toMap(LessonProgress::getLessonId, Function.identity()));
        Map<Long, String> creatorUuids = resolveCreatorUuids(page.getContent());

        return PageResponse.from(page.map(l ->
                toResponse(l, creatorUuids, progressByLesson.get(l.getId()))));
    }

    @Transactional(readOnly = true)
    public VideoLessonResponse get(Long id, Long callerUserId) {
        VideoLesson lesson = requireLesson(id);
        LessonProgress progress = progressRepository.findByLessonIdAndUserId(id, callerUserId).orElse(null);
        return toResponse(lesson, resolveCreatorUuids(List.of(lesson)), progress);
    }

    @Transactional
    public VideoLessonResponse create(CreateVideoLessonRequest request, Long callerUserId) {
        VideoLesson lesson = new VideoLesson();
        lesson.setTitle(request.title());
        lesson.setDescription(blankToNull(request.description()));
        lesson.setModuleName(request.moduleName());
        lesson.setTrack(blankToNull(request.track()));
        lesson.setVideoUrl(request.videoUrl());
        lesson.setDurationSeconds(request.durationSeconds());
        lesson.setSortOrder(request.sortOrder());
        lesson.setCreatedBy(callerUserId);
        lesson.setPublished(request.published());
        lessonRepository.save(lesson);

        log.info("[lessons] {} created lesson {} (module {})", callerUserId, lesson.getId(), lesson.getModuleName());
        return toResponse(lesson, resolveCreatorUuids(List.of(lesson)), null);
    }

    @Transactional
    public VideoLessonResponse update(Long id, UpdateVideoLessonRequest request, Long callerUserId) {
        VideoLesson lesson = requireLesson(id);
        requireCreatorOrAdmin(lesson, callerUserId);

        lesson.setTitle(request.title());
        lesson.setDescription(blankToNull(request.description()));
        lesson.setModuleName(request.moduleName());
        lesson.setTrack(blankToNull(request.track()));
        lesson.setVideoUrl(request.videoUrl());
        lesson.setDurationSeconds(request.durationSeconds());
        lesson.setSortOrder(request.sortOrder());
        lesson.setPublished(request.published());

        return toResponse(lesson, resolveCreatorUuids(List.of(lesson)), null);
    }

    /**
     * {@code DELETE /api/v1/lessons/{id}} — a lesson that no learner has touched (no progress
     * rows, no quiz attempts) is genuinely row-deleted along with its quiz questions; one with
     * learner history is only unpublished, so that history survives. Idempotent.
     */
    @Transactional
    public void unpublish(Long id, Long callerUserId) {
        VideoLesson lesson = requireLesson(id);
        requireCreatorOrAdmin(lesson, callerUserId);

        if (progressRepository.countByLessonId(id) == 0 && quizAttemptRepository.countByLessonId(id) == 0) {
            quizQuestionRepository.deleteByLessonId(id);
            lessonRepository.delete(lesson);
            log.info("[lessons] {} deleted untouched lesson {}", callerUserId, id);
            return;
        }
        lesson.setPublished(false);
    }

    /**
     * {@code POST /api/v1/lessons/{id}/progress} — upsert the caller's progress. {@code
     * watchedSeconds} is clamped to never move backwards; {@code completed} once true stays true
     * and stamps {@code completedAt} exactly once. A learner may only report progress on a
     * published lesson (an unpublished one 404s for them the same as it does on read).
     */
    @Transactional
    public VideoLessonResponse recordProgress(Long id, RecordLessonProgressRequest request, Long callerUserId) {
        VideoLesson lesson = requireLesson(id);
        if (!lesson.isPublished() && !SecurityUtils.currentUserRoles().contains(RoleCode.ADMIN.name())
                && !lesson.getCreatedBy().equals(callerUserId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, id);
        }

        LessonProgress progress = progressRepository.findByLessonIdAndUserId(id, callerUserId)
                .orElseGet(() -> {
                    LessonProgress fresh = new LessonProgress();
                    fresh.setLessonId(id);
                    fresh.setUserId(callerUserId);
                    return fresh;
                });

        progress.setWatchedSeconds(Math.max(progress.getWatchedSeconds(), request.watchedSeconds()));
        if (request.completed() && progress.getStatus() != LessonProgressStatus.COMPLETED) {
            progress.setStatus(LessonProgressStatus.COMPLETED);
            progress.setCompletedAt(java.time.Instant.now());
        } else if (!request.completed() && progress.getStatus() != LessonProgressStatus.COMPLETED) {
            progress.setStatus(LessonProgressStatus.IN_PROGRESS);
        }
        progressRepository.save(progress);

        return toResponse(lesson, resolveCreatorUuids(List.of(lesson)), progress);
    }

    @Transactional(readOnly = true)
    public PageResponse<MyLessonProgressItem> myProgress(Long callerUserId, Pageable pageable) {
        Page<LessonProgress> page = progressRepository.findByUserId(callerUserId, pageable);
        List<Long> lessonIds = page.getContent().stream().map(LessonProgress::getLessonId).toList();
        Map<Long, VideoLesson> lessonsById = lessonIds.isEmpty()
                ? Map.of()
                : lessonRepository.findAllById(lessonIds).stream()
                        .collect(Collectors.toMap(VideoLesson::getId, Function.identity()));

        return PageResponse.from(page.map(p -> {
            VideoLesson l = lessonsById.get(p.getLessonId());
            return new MyLessonProgressItem(
                    p.getLessonId(),
                    l == null ? null : l.getTitle(),
                    l == null ? null : l.getModuleName(),
                    p.getStatus().name(),
                    p.getWatchedSeconds(),
                    l == null ? null : l.getDurationSeconds(),
                    p.getCompletedAt(),
                    p.getUpdatedAt());
        }));
    }

    @Transactional(readOnly = true)
    public List<ModuleSummaryResponse> modules() {
        return lessonRepository.summariseModules().stream()
                .map(m -> new ModuleSummaryResponse(m.getModuleName(), m.getLessonCount()))
                .toList();
    }

    VideoLesson requireLesson(Long id) {
        return lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, id));
    }

    void requireCreatorOrAdmin(VideoLesson lesson, Long callerUserId) {
        if (SecurityUtils.currentUserRoles().contains(RoleCode.ADMIN.name())) {
            return;
        }
        if (!lesson.getCreatedBy().equals(callerUserId)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }

    private boolean isCurator() {
        return SecurityUtils.currentUserRoles().stream().anyMatch(CURATOR_ROLES::contains);
    }

    private Map<Long, String> resolveCreatorUuids(List<VideoLesson> lessons) {
        List<Long> ids = lessons.stream().map(VideoLesson::getCreatedBy).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getUuid));
    }

    static VideoLessonResponse toResponse(VideoLesson l, Map<Long, String> creatorUuids, LessonProgress progress) {
        LessonProgressView view = progress == null
                ? LessonProgressView.notStarted()
                : new LessonProgressView(progress.getStatus().name(), progress.getWatchedSeconds(), progress.getCompletedAt());
        return new VideoLessonResponse(
                l.getId(), l.getTitle(), l.getDescription(), l.getModuleName(), l.getTrack(), l.getVideoUrl(),
                l.getDurationSeconds(), l.getSortOrder(), l.isPublished(),
                creatorUuids.get(l.getCreatedBy()), view, l.getCreatedAt(), l.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
