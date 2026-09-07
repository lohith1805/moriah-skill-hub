package com.moriah.skillhub.user;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.user.dto.ResumeDownloadResponse;
import com.moriah.skillhub.user.dto.UserProfileResponse;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserProfile;
import com.moriah.skillhub.user.repository.UserProfileRepository;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;

/**
 * build-plan.md feature 09: "Resume to {@code resumes/{userUuid}/resume.pdf}, key stored on the
 * profile." Content type is fixed to {@code application/pdf} regardless of what the client's
 * multipart part claims — {@code StorageService.upload}'s magic-byte check (feature 08) still
 * rejects anything that isn't genuinely PDF-shaped, which is the actual security boundary, not
 * the declared content type.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeService {

    private static final String RESUME_CONTENT_TYPE = "application/pdf";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProfileService profileService;
    private final StorageService storageService;

    @Transactional
    public UserProfileResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE, "No file was uploaded.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
        UserProfile profile = profileService.getOrCreateProfile(user);

        String key = "resumes/" + user.getUuid() + "/resume.pdf";
        storageService.upload(key, readBytes(file), RESUME_CONTENT_TYPE);

        profile.setResumeKey(key);
        profileService.recalculateCompletion(profile, user);
        return profileService.toResponse(user, profile);
    }

    @Transactional(readOnly = true)
    public ResumeDownloadResponse getDownloadUrl(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .filter(p -> p.getResumeKey() != null)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESUME_NOT_FOUND));

        Duration ttl = Duration.ofMinutes(Constants.PRESIGNED_URL_TTL_MINUTES);
        URL url = storageService.presignedGetUrl(user.getUuid(), profile.getResumeKey(), ttl);
        return new ResumeDownloadResponse(url, Instant.now().plus(ttl));
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("[user/resume] failed to read uploaded file", e);
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED);
        }
    }
}
