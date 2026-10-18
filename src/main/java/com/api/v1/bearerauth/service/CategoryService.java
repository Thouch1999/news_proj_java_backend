package com.api.v1.bearerauth.service;

import com.api.v1.bearerauth.model.request.CategoryRequest;
import com.api.v1.bearerauth.model.response.CategoryDto;

import java.util.List;

public interface CategoryService {
    /** Public: active categories ordered by name. */
    List<CategoryDto> findActive();

    /** Public: an active category by slug (404 when it does not exist or is inactive). */
    CategoryDto findBySlug(String slug);

    /** Admin: every category with the number of news it has. */
    List<CategoryDto> findAllForAdmin();

    CategoryDto create(CategoryRequest request);

    /** Updates name and description; the slug changes only when a different slug is sent. */
    CategoryDto update(Long id, CategoryRequest request);

    CategoryDto setActive(Long id, boolean active);

    /** Deletes a category that has no news (409 otherwise: deactivate it instead). */
    void delete(Long id);
}
