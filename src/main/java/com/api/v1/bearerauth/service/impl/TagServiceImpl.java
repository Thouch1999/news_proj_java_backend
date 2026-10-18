package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.Tag;
import com.api.v1.bearerauth.model.request.TagRequest;
import com.api.v1.bearerauth.model.response.TagDto;
import com.api.v1.bearerauth.repository.NewsTagRepository;
import com.api.v1.bearerauth.repository.TagRepository;
import com.api.v1.bearerauth.service.TagService;
import com.api.v1.bearerauth.util.SlugUtil;
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
        return mapper.map(tagRepository.save(tag), TagDto.class);
    }

    @Override
    @Transactional
    public TagDto update(Long id, TagRequest request) {
        Tag tag = find(id);
        String name = request.getName().trim();
        if (tagRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.TAG_NAME_EXISTS.getDefaultMessage());
        }

        tag.setName(name);
        // renaming keeps the slug (links stay valid); it changes only when a different one is sent
        String requested = request.getSlug();
        if (requested != null && !requested.isBlank() && !requested.trim().equals(tag.getSlug())) {
            tag.setSlug(SlugUtil.resolve(requested, name, slug -> tagRepository.existsBySlugAndIdNot(slug, id)));
        }
        return mapper.map(tagRepository.save(tag), TagDto.class);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Tag tag = find(id);
        newsTagRepository.deleteAllByTagId(id);
        tagRepository.delete(tag);
    }

    private Tag find(Long id) {
        return tagRepository.findById(id).orElseThrow(() -> notFound());
    }

    private CustomException notFound() {
        return new CustomException(HttpStatus.NOT_FOUND, MessageConstant.TAG_NOT_FOUND.getDefaultMessage());
    }
}
