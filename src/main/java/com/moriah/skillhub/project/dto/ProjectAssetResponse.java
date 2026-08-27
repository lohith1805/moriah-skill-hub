package com.moriah.skillhub.project.dto;

import com.moriah.skillhub.project.entity.AssetType;

/** {@code url} is always a working, directly-usable link — a fresh presigned GET URL when the
 * asset is backed by {@code fileKey}, the stored {@code externalUrl} verbatim otherwise. Never
 * exposes the raw {@code fileKey} (an internal storage detail, not a stable public reference —
 * matches build-plan.md feature 15's verify line: "Assets return working presigned URLs"). */
public record ProjectAssetResponse(
        Long id,
        AssetType assetType,
        String title,
        String url,
        Integer sortOrder
) {
}
