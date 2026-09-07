package com.moriah.skillhub.project.dto;

import com.moriah.skillhub.project.entity.ProjectDifficulty;

/** {@code brokenCodeUrl}/{@code testScriptUrl} are fresh presigned GET URLs, same "never expose
 * the raw key" reasoning as {@link ProjectAssetResponse#url()}. {@code testScriptUrl} is {@code
 * null} when the challenge has no test script (V8's {@code test_script_key} is nullable). */
public record ChallengeResponse(
        Long id,
        String title,
        String expectedBehaviour,
        String brokenCodeUrl,
        String testScriptUrl,
        ProjectDifficulty difficulty
) {
}
