package com.moriah.skillhub.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * architecture.md's sample {@code application.yml} names {@code moriah.storage.bucket}/
 * {@code .endpoint} literally — {@code region}/{@code accessKey}/{@code secretKey} added here
 * since a real {@link software.amazon.awssdk.services.s3.S3Client} needs all five. {@code
 * endpoint} blank in a real AWS deployment; set to the MinIO/R2 endpoint locally and in any
 * environment not using AWS directly (library-docs.md "AWS SDK v2 (S3 / R2)": "Setting
 * S3_ENDPOINT switches to Cloudflare R2 with no code change — keep it that way").
 */
@ConfigurationProperties(prefix = "moriah.storage")
public record StorageProperties(String bucket, String endpoint, String region, String accessKey, String secretKey) {
}
