package com.moriah.skillhub.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * The shape every paginated list endpoint returns as {@code ApiResponse<PageResponse<T>>}.
 * A plain {@code Page<T>} is never returned directly — Spring's default {@code Page}
 * serialization is not part of this project's response contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
