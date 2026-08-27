package com.moriah.skillhub.hr.dto;

import java.time.Instant;

/** No {@code hr_letters} table exists in architecture.md's V12 schema — a letter is generated,
 * uploaded, and its download URL handed back in one call; nothing about the issuance itself is
 * persisted beyond the S3 object (see {@code HrLetterService}'s own Javadoc). */
public record LetterResponse(String downloadUrl, Instant expiresAt) {
}
