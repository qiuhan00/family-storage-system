package com.family.storage.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 统一分页响应：{content, totalElements, totalPages, size, number}。
 */
public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int size, int number) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                page.getSize(), page.getNumber());
    }
}