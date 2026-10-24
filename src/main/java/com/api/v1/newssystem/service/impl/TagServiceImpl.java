package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.ActivityTargetType;
import com.api.v1.newssystem.service.ActivityLogService;
import com.api.v1.newssystem.util.ActivityDetails;
import com.api.v1.newssystem.model.entity.Tag;
import com.api.v1.newssystem.model.request.TagRequest;
import com.api.v1.newssystem.model.response.TagDto;
import com.api.v1.newssystem.repository.NewsTagRepository;
import com.api.v1.newssystem.repository.TagRepository;
import com.api.v1.newssystem.service.TagService;
import com.api.v1.newssystem.util.SlugUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {
    private final TagRepository tagRepository;
    private final NewsTagRepository newsTagRepository;
    private final ModelMapper mapper;
    private final ActivityLogService activityLogService;

    @Override
    public List<TagDto> findAll() {
        return tagRepository.findAllByOrderByNameAsc().stream()
                .map(tag -> mapper.map(tag, TagDto.class))
                .toList();
    }

    @Override
    public TagDto findBySlug(String slug) {
        return tagRepository.findBySlug(slug)
                .map(tag -> mapper.map(tag, TagDto.class))
                .orElseThrow(() -> notFound());
    }

    @Override
    @Transactional
    public TagDto create(TagRequest request) {
        String name = request.getName().trim();
        if (tagRepository.existsByNameIgnoreCase(name)) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.TAG_NAME_EXISTS.getDefaultMessage());
        }

        Tag tag = new Tag();
        tag.setName(name);
        tag.setSlug(SlugUtil.resolve(request.getSlug(), name, tagRepository::existsBySlug));
        Tag saved = tagRepository.save(tag);
        activityLogService.record(ActivityAction.TAG_CREATED, ActivityTargetType.TAG, saved.getId(), saved.getName(),
                ActivityDetails.of("slug", saved.getSlug()));
        return mapper.map(saved, TagDto.class);
    }

    @Override
    @Transactional
    public TagDto update(Long id, TagRequest request) {
        Tag tag = find(id);
        String name = request.getName().trim();
        if (tagRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.TAG_NAME_EXISTS.getDefaultMessage());
        }

        String nameBefore = tag.getName();
        String slugBefore = tag.getSlug();
        tag.setName(name);
        // renaming keeps the slug (links stay valid); it changes only when a different one is sent
        String requested = request.getSlug();
        if (requested != null && !requested.isBlank() && !requested.trim().equals(tag.getSlug())) {
            tag.setSlug(SlugUtil.resolve(requested, name, slug -> tagRepository.existsBySlugAndIdNot(slug, id)));
        }
        Tag saved = tagRepository.save(tag);
        ActivityDetails.Changes changes = new ActivityDetails.Changes()
                .field("name", nameBefore, saved.getName())
                .field("slug", slugBefore, saved.getSlug());
        if (!changes.isEmpty()) {
            activityLogService.record(ActivityAction.TAG_UPDATED, ActivityTargetType.TAG, saved.getId(), saved.getName(), changes.toDetails());
        }
        return mapper.map(saved, TagDto.class);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Tag tag = find(id);
        newsTagRepository.deleteAllByTagId(id);
        tagRepository.delete(tag);
        activityLogService.record(ActivityAction.TAG_DELETED, ActivityTargetType.TAG, id, tag.getName(),
                ActivityDetails.of("slug", tag.getSlug()));
    }

    private Tag find(Long id) {
        return tagRepository.findById(id).orElseThrow(() -> notFound());
    }

    private CustomException notFound() {
        return new CustomException(HttpStatus.NOT_FOUND, MessageConstant.TAG_NOT_FOUND.getDefaultMessage());
    }
}
