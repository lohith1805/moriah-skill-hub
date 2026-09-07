package com.moriah.skillhub.common.storage;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.security.OwnershipGuard;
import com.moriah.skillhub.common.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URL;
import java.time.Duration;

/**
 * library-docs.md "AWS SDK v2 (S3 / R2)", applied directly. Private bucket, presigned GET only,
 * never {@code PublicRead}. Never write to local disk — the byte array goes straight to S3.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;
    private final OwnershipGuard ownershipGuard;

    @Override
    public String upload(String key, byte[] content, String contentType) {
        if (content.length > Constants.MAX_UPLOAD_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        if (!FileSignatures.isSupported(contentType) || !FileSignatures.matches(contentType, content)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        return uploadTrusted(key, content, contentType);
    }

    @Override
    public String uploadTrusted(String key, byte[] content, String contentType) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(storageProperties.bucket())
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
            return key;
        } catch (SdkException e) {
            log.error("[storage] upload failed for key {}", key, e);
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED);
        }
    }

    @Override
    public URL presignedGetUrl(String callerUuid, String key, Duration ttl) {
        // MANDATORY — signing a key because the caller asked for it is an IDOR.
        ownershipGuard.requireKeyAccess(callerUuid, key);

        try {
            return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(b -> b.bucket(storageProperties.bucket()).key(key))
                    .build()).url();
        } catch (S3Exception e) {
            log.error("[storage] presign failed for key {}", key, e);
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED);
        }
    }
}
