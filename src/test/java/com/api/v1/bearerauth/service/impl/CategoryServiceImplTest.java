package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.config.MapperConfig;
import com.api.v1.bearerauth.model.entity.Category;
import com.api.v1.bearerauth.model.request.CategoryRequest;
import com.api.v1.bearerauth.model.response.CategoryDto;
import com.api.v1.bearerauth.repository.CategoryRepository;
import com.api.v1.bearerauth.repository.NewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private NewsRepository newsRepository;

    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryServiceImpl(categoryRepository, newsRepository, new MapperConfig().modelMapper());
    }

    private Category category(long id, String name, String slug, boolean active) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSlug(slug);
        category.setActive(active);
        return category;
    }

    private CategoryRequest request(String name, String slug) {
        CategoryRequest request = new CategoryRequest();
        request.setName(name);
        request.setSlug(slug);
        return request;
    }

    private void assertFails(Runnable call, MessageConstant expected, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(expected.getDefaultMessage());
                    assertThat(e.getHttpStatus()).isEqualTo(status);
                });
    }

    @Test
    void create_generatesSlugFromName_andStartsActive() {
        when(categoryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        CategoryDto created = service.create(request("  Technology News ", null));

        assertThat(created.getName()).isEqualTo("Technology News");
        assertThat(created.getSlug()).isEqualTo("technology-news");
        assertThat(created.isActive()).isTrue();
        assertThat(created.getNewsCount()).isZero();
    }

    @Test
    void create_makesTheSlugUnique() {
        when(categoryRepository.existsBySlug("sports")).thenReturn(true);
        when(categoryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.create(request("Sports", null)).getSlug()).isEqualTo("sports-2");
    }

    @Test
    void create_neverUsesTheReservedAdminSlug() {
        when(categoryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.create(request("Admin", null)).getSlug()).isEqualTo("admin-2");
        assertFails(() -> service.create(request("កីឡា", "admin")), MessageConstant.SLUG_ALREADY_EXISTS, HttpStatus.CONFLICT);
    }

    @Test
    void create_duplicateName_isRejected() {
        when(categoryRepository.existsByNameIgnoreCase("Sports")).thenReturn(true);

        assertFails(() -> service.create(request("Sports", null)), MessageConstant.CATEGORY_NAME_EXISTS, HttpStatus.CONFLICT);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_khmerNameNeedsASlug() {
        assertFails(() -> service.create(request("កីឡា", null)), MessageConstant.SLUG_REQUIRED, HttpStatus.BAD_REQUEST);
        verify(categoryRepository, never()).save(any());

        when(categoryRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        CategoryDto created = service.create(request("កីឡា", "sports-km"));

        assertThat(created.getName()).isEqualTo("កីឡា");
        assertThat(created.getSlug()).isEqualTo("sports-km");
    }

    @Test
    void update_renamingKeepsTheSlug() {
        Category sports = category(1, "Sports", "sports", true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sports));
        when(categoryRepository.save(sports)).thenReturn(sports);
        when(newsRepository.countByCategoryId(1L)).thenReturn(4L);

        CategoryDto updated = service.update(1L, request("Sport and Games", null));

        assertThat(updated.getName()).isEqualTo("Sport and Games");
        assertThat(updated.getSlug()).isEqualTo("sports");
        assertThat(updated.getNewsCount()).isEqualTo(4L);
    }

    @Test
    void update_changesTheSlugOnlyWhenADifferentOneIsSent_andChecksItIsFree() {
        Category sports = category(1, "Sports", "sports", true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sports));
        when(categoryRepository.save(sports)).thenReturn(sports);

        service.update(1L, request("Sports", "sport"));
        assertThat(sports.getSlug()).isEqualTo("sport");

        when(categoryRepository.existsBySlugAndIdNot("health", 1L)).thenReturn(true);
        assertFails(() -> service.update(1L, request("Sports", "health")), MessageConstant.SLUG_ALREADY_EXISTS, HttpStatus.CONFLICT);
        assertThat(sports.getSlug()).isEqualTo("sport");
    }

    @Test
    void update_nameUsedByAnotherCategory_isRejected_andUnknownIdIsNotFound() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1, "Sports", "sports", true)));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Health", 1L)).thenReturn(true);

        assertFails(() -> service.update(1L, request("Health", null)), MessageConstant.CATEGORY_NAME_EXISTS, HttpStatus.CONFLICT);

        when(categoryRepository.findById(9L)).thenReturn(Optional.empty());
        assertFails(() -> service.update(9L, request("Any", null)), MessageConstant.CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @Test
    void setActive_deactivatesAndActivates() {
        Category sports = category(1, "Sports", "sports", true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sports));
        when(categoryRepository.save(sports)).thenReturn(sports);

        assertThat(service.setActive(1L, false).isActive()).isFalse();
        assertThat(service.setActive(1L, true).isActive()).isTrue();
    }

    @Test
    void delete_categoryWithNews_isRejected_andNotDeleted() {
        Category sports = category(1, "Sports", "sports", true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sports));
        when(newsRepository.countByCategoryId(1L)).thenReturn(3L);

        assertFails(() -> service.delete(1L), MessageConstant.CATEGORY_HAS_NEWS, HttpStatus.CONFLICT);

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void delete_categoryWithoutNews_isDeleted() {
        Category sports = category(1, "Sports", "sports", true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sports));
        when(newsRepository.countByCategoryId(1L)).thenReturn(0L);

        service.delete(1L);

        verify(categoryRepository).delete(sports);
    }

    @Test
    void findActive_returnsOnlyWhatTheActiveQueryGives_withoutCounts() {
        when(categoryRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(category(1, "Sports", "sports", true)));

        List<CategoryDto> result = service.findActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNewsCount()).isNull();
    }

    @Test
    void findBySlug_inactiveOrUnknownCategory_isNotFound() {
        when(categoryRepository.findBySlugAndActiveTrue("old")).thenReturn(Optional.empty());

        assertFails(() -> service.findBySlug("old"), MessageConstant.CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @Test
    void findAllForAdmin_includesInactiveCategories_withNewsCounts() {
        when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(
                category(1, "Business", "business", true),
                category(2, "Sports", "sports", false)));
        when(newsRepository.countNewsPerCategory()).thenReturn(List.<Object[]>of(new Object[]{2L, 5L}));

        List<CategoryDto> result = service.findAllForAdmin();

        assertThat(result).extracting(CategoryDto::getNewsCount).containsExactly(0L, 5L);
        assertThat(result).extracting(CategoryDto::isActive).containsExactly(true, false);
    }

    @Test
    void create_savesTheEntityWithTrimmedDescription() {
        when(categoryRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        CategoryRequest request = request("Health", null);
        request.setDescription("  Medical news  ");

        service.create(request);

        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(saved.capture());
        assertThat(saved.getValue().getDescription()).isEqualTo("Medical news");
    }
}
