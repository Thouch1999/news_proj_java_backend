package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.config.MapperConfig;
import com.api.v1.bearerauth.model.entity.Tag;
import com.api.v1.bearerauth.model.request.TagRequest;
import com.api.v1.bearerauth.model.response.TagDto;
import com.api.v1.bearerauth.repository.NewsTagRepository;
import com.api.v1.bearerauth.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceImplTest {
    @Mock
    private TagRepository tagRepository;
    @Mock
    private NewsTagRepository newsTagRepository;

    private TagServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TagServiceImpl(tagRepository, newsTagRepository, new MapperConfig().modelMapper());
    }

    private Tag tag(long id, String name, String slug) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tag.setSlug(slug);
        return tag;
    }

    private TagRequest request(String name, String slug) {
        TagRequest request = new TagRequest();
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
    void create_generatesUniqueSlug() {
        when(tagRepository.existsBySlug("breaking-news")).thenReturn(true);
        when(tagRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        TagDto created = service.create(request(" Breaking News ", null));

        assertThat(created.getName()).isEqualTo("Breaking News");
        assertThat(created.getSlug()).isEqualTo("breaking-news-2");
    }

    @Test
    void create_duplicateName_isRejected_andKhmerNameNeedsASlug() {
        when(tagRepository.existsByNameIgnoreCase("Politics")).thenReturn(true);
        assertFails(() -> service.create(request("Politics", null)), MessageConstant.TAG_NAME_EXISTS, HttpStatus.CONFLICT);

        assertFails(() -> service.create(request("នយោបាយ", null)), MessageConstant.SLUG_REQUIRED, HttpStatus.BAD_REQUEST);

        when(tagRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.create(request("នយោបាយ", "politics-km")).getSlug()).isEqualTo("politics-km");
    }

    @Test
    void update_renamingKeepsTheSlug_andANewSlugMustBeFree() {
        Tag politics = tag(1, "Politics", "politics");
        when(tagRepository.findById(1L)).thenReturn(Optional.of(politics));
        when(tagRepository.save(politics)).thenReturn(politics);

        assertThat(service.update(1L, request("World Politics", null)).getSlug()).isEqualTo("politics");

        when(tagRepository.existsBySlugAndIdNot("economy", 1L)).thenReturn(true);
        assertFails(() -> service.update(1L, request("World Politics", "economy")), MessageConstant.SLUG_ALREADY_EXISTS, HttpStatus.CONFLICT);

        assertThat(service.update(1L, request("World Politics", "world-politics")).getSlug()).isEqualTo("world-politics");
    }

    @Test
    void update_nameUsedByAnotherTag_isRejected() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag(1, "Politics", "politics")));
        when(tagRepository.existsByNameIgnoreCaseAndIdNot("Economy", 1L)).thenReturn(true);

        assertFails(() -> service.update(1L, request("Economy", null)), MessageConstant.TAG_NAME_EXISTS, HttpStatus.CONFLICT);
    }

    @Test
    void delete_removesLinksToNewsThenTheTag() {
        Tag politics = tag(1, "Politics", "politics");
        when(tagRepository.findById(1L)).thenReturn(Optional.of(politics));

        service.delete(1L);

        InOrder order = inOrder(newsTagRepository, tagRepository);
        order.verify(newsTagRepository).deleteAllByTagId(1L);
        order.verify(tagRepository).delete(politics);
    }

    @Test
    void delete_unknownTag_isNotFound() {
        when(tagRepository.findById(9L)).thenReturn(Optional.empty());

        assertFails(() -> service.delete(9L), MessageConstant.TAG_NOT_FOUND, HttpStatus.NOT_FOUND);

        verify(newsTagRepository, never()).deleteAllByTagId(any());
    }

    @Test
    void findBySlug_unknownTag_isNotFound() {
        when(tagRepository.findBySlug("nope")).thenReturn(Optional.empty());

        assertFails(() -> service.findBySlug("nope"), MessageConstant.TAG_NOT_FOUND, HttpStatus.NOT_FOUND);
    }
}
