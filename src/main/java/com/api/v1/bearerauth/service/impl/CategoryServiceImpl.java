package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.Category;
import com.api.v1.bearerauth.model.request.CategoryRequest;
import com.api.v1.bearerauth.model.response.CategoryDto;
import com.api.v1.bearerauth.repository.CategoryRepository;
import com.api.v1.bearerauth.repository.NewsRepository;
import com.api.v1.bearerauth.service.CategoryService;
import com.api.v1.bearerauth.util.SlugUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    // GET /api/v1/categories/admin is a fixed path, so no category may use this slug
    private static final Set<String> RESERVED_SLUGS = Set.of("admin");

    private final CategoryRepository categoryRepository;
    private final NewsRepository newsRepository;
    private final ModelMapper mapper;

    @Override
    public List<CategoryDto> findActive() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(category -> mapper.map(category, CategoryDto.class))
                .toList();
    }

    @Override
    public CategoryDto findBySlug(String slug) {
        return categoryRepository.findBySlugAndActiveTrue(slug)
                .map(category -> mapper.map(category, CategoryDto.class))
                .orElseThrow(() -> notFound());
    }

    @Override
    public List<CategoryDto> findAllForAdmin() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : newsRepository.countNewsPerCategory()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(category -> toAdminDto(category, counts.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public CategoryDto create(CategoryRequest request) {
        String name = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.CATEGORY_NAME_EXISTS.getDefaultMessage());
        }

        Category category = new Category();
        category.setName(name);
        category.setSlug(SlugUtil.resolve(request.getSlug(), name, this::slugTaken));
        category.setDescription(blankToNull(request.getDescription()));
        category.setActive(true);
        return toAdminDto(categoryRepository.save(category), 0L);
    }

    @Override
    @Transactional
    public CategoryDto update(Long id, CategoryRequest request) {
        Category category = find(id);
        String name = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.CATEGORY_NAME_EXISTS.getDefaultMessage());
        }

        category.setName(name);
        category.setDescription(blankToNull(request.getDescription()));
        // renaming keeps the slug (links stay valid); it changes only when a different one is sent
        String requested = request.getSlug();
        if (requested != null && !requested.isBlank() && !requested.trim().equals(category.getSlug())) {
            category.setSlug(SlugUtil.resolve(requested, name,
                    slug -> RESERVED_SLUGS.contains(slug) || categoryRepository.existsBySlugAndIdNot(slug, id)));
        }
        return toAdminDto(categoryRepository.save(category), newsRepository.countByCategoryId(id));
    }

    @Override
    @Transactional
    public CategoryDto setActive(Long id, boolean active) {
        Category category = find(id);
        category.setActive(active);
        return toAdminDto(categoryRepository.save(category), newsRepository.countByCategoryId(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        if (newsRepository.countByCategoryId(id) > 0) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.CATEGORY_HAS_NEWS.getDefaultMessage());
        }
        categoryRepository.delete(category);
    }

    private boolean slugTaken(String slug) {
        return RESERVED_SLUGS.contains(slug) || categoryRepository.existsBySlug(slug);
    }

    private CategoryDto toAdminDto(Category category, long newsCount) {
        CategoryDto dto = mapper.map(category, CategoryDto.class);
        dto.setNewsCount(newsCount);
        return dto;
    }

    private Category find(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> notFound());
    }

    private CustomException notFound() {
        return new CustomException(HttpStatus.NOT_FOUND, MessageConstant.CATEGORY_NOT_FOUND.getDefaultMessage());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
