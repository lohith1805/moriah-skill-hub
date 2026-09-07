package com.moriah.skillhub.common.storage;

import java.net.URL;
import java.time.Duration;

/**
 * architecture.md "Object Storage" / package diagram: {@code common/storage/}. Interface kept
 * separate from {@link S3StorageService} so a future feature or test can substitute an
 * implementation without touching every call site — matches {@code StorageService}/
 * {@code S3StorageService} naming in code-standards.md's own interface-naming example.
 */
public interface StorageService {

    /**
     * Validates content type + magic bytes + size (code-standards.md "File uploads"), then
     * uploads and returns the key. Never write to local disk — the byte array goes straight to
     * S3 (library-docs.md "AWS SDK v2 (S3 / R2)").
     *
     * @throws com.moriah.skillhub.common.exception.BusinessException {@code UNSUPPORTED_FILE_TYPE}
     *         if the content type or magic bytes aren't recognized, {@code FILE_TOO_LARGE} over
     *         {@link com.moriah.skillhub.common.util.Constants#MAX_UPLOAD_BYTES}
     */
    String upload(String key, byte[] content, String contentType);

    /**
     * Uploads pre-validated content — for server-generated files (invoice PDFs, certificates)
     * that never pass through {@link #upload}'s client-upload validation, since there's no
     * untrusted client input to validate in the first place.
     */
    String uploadTrusted(String key, byte[] content, String contentType);

    /**
     * MANDATORY authorization before signing (library-docs.md "AWS SDK v2 (S3 / R2)": "signing a
     * key because the caller asked for it is an IDOR") — {@code callerUuid} matches {@link
     * com.moriah.skillhub.common.security.OwnershipGuard#requireKeyAccess}'s existing uuid-based
     * signature, not a userId, since every self-owned key namespace embeds the owner's uuid.
     */
    URL presignedGetUrl(String callerUuid, String key, Duration ttl);
}
