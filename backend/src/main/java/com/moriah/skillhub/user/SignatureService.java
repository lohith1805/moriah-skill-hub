package com.moriah.skillhub.user;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.user.dto.SignatureResponse;
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
import java.util.Arrays;

/**
 * A saved handwritten-signature image per user (V51), mirroring {@link ResumeService}. Stored at
 * {@code signatures/{userUuid}/signature.<png|jpg>}; the offer-letter e-sign flow stamps it so a
 * student never re-draws their signature. The content type is sniffed from the file's own magic
 * bytes (PNG / JPEG), never trusted from the multipart part — same boundary {@code
 * StorageService.upload} enforces for every other upload.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignatureService {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProfileService profileService;
    private final StorageService storageService;

    @Transactional
    public SignatureResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE, "No file was uploaded.");
        }
        byte[] bytes = readBytes(file);

        String contentType;
        String ext;
        if (startsWith(bytes, PNG_MAGIC)) {
            contentType = "image/png";
            ext = "png";
        } else if (startsWith(bytes, JPEG_MAGIC)) {
            contentType = "image/jpeg";
            ext = "jpg";
        } else {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "The signature must be a PNG or JPEG image.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
        UserProfile profile = profileService.getOrCreateProfile(user);

        String key = "signatures/" + user.getUuid() + "/signature." + ext;
        storageService.upload(key, bytes, contentType);
        profile.setSignatureKey(key);
        userProfileRepository.save(profile);

        return presign(user, key);
    }

    @Transactional(readOnly = true)
    public SignatureResponse getDownloadUrl(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .filter(p -> p.getSignatureKey() != null)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SIGNATURE_NOT_FOUND));
        return presign(user, profile.getSignatureKey());
    }

    private SignatureResponse presign(User user, String key) {
        Duration ttl = Duration.ofMinutes(Constants.PRESIGNED_URL_TTL_MINUTES);
        URL url = storageService.presignedGetUrl(user.getUuid(), key, ttl);
        return new SignatureResponse(url, Instant.now().plus(ttl));
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOf(content, prefix.length), prefix);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("[user/signature] failed to read uploaded file", e);
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED);
        }
    }
}
