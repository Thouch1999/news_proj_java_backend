package com.api.v1.newssystem.service;

import com.api.v1.newssystem.model.request.TagRequest;
import com.api.v1.newssystem.model.response.TagDto;

import java.util.List;

public interface TagService {
    List<TagDto> findAll();

    TagDto findBySlug(String slug);

    TagDto create(TagRequest request);

    /** Updates the name; the slug changes only when a different slug is sent. */
    TagDto update(Long id, TagRequest request);

    /** Deletes the tag and its links to news. */
    void delete(Long id);
}
