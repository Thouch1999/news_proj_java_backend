package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.Bookmark;
import com.api.v1.bearerauth.model.entity.News;
import com.api.v1.bearerauth.model.response.BookmarkDto;
import com.api.v1.bearerauth.model.response.PublicNewsSummaryDto;
import com.api.v1.bearerauth.repository.AppUserRepository;
import com.api.v1.bearerauth.repository.BookmarkRepository;
import com.api.v1.bearerauth.repository.NewsRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.service.BookmarkService;
import com.api.v1.bearerauth.service.PublicNewsService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookmarkServiceImpl implements BookmarkService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final BookmarkRepository bookmarkRepository;
    private final NewsRepository newsRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final PublicNewsService publicNewsService;

    // Not one big transaction on purpose: when two requests save the same article at the same moment, the second
    // insert hits the unique (user, news) constraint. That exception is caught here and means "already saved";
    // inside one outer transaction it would have marked the whole transaction for rollback.
    @Override
    public boolean add(Long newsId) {
        AppUser user = currentUserService.getCurrentUser();
        News news = newsRepository.findPublishedById(newsId)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.NEWS_NOT_FOUND.getDefaultMessage()));
        if (bookmarkRepository.existsByUserIdAndNewsId(user.getId(), news.getId())) {
            return false;
        }

        Bookmark bookmark = new Bookmark();
        bookmark.setUser(appUserRepository.getReferenceById(user.getId()));
        bookmark.setNews(news);
        try {
            bookmarkRepository.saveAndFlush(bookmark);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false; // the same bookmark was created a moment ago by another request
        }
    }

    @Override
    @Transactional
    public void remove(Long newsId) {
        bookmarkRepository.deleteByUserIdAndNewsId(currentUserService.getCurrentUser().getId(), newsId);
    }

    @Override
    @Transactional
    public Page<BookmarkDto> list(int page, int size) {
        Long userId = currentUserService.getCurrentUser().getId();
        Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), MAX_PAGE_SIZE), NEWEST);
        Page<Bookmark> found = bookmarkRepository.findReadableByUserId(userId, pageable);

        // the cards of the whole page are built together: tags and comment counts cost one query each
        List<PublicNewsSummaryDto> cards = publicNewsService.toCards(found.getContent().stream().map(Bookmark::getNews).toList());
        List<BookmarkDto> items = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            items.add(new BookmarkDto(found.getContent().get(i).getCreatedAt(), cards.get(i)));
        }
        return new PageImpl<>(items, pageable, found.getTotalElements());
    }
}
