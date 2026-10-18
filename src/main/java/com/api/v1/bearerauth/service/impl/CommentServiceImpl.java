package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.Comment;
import com.api.v1.bearerauth.model.entity.News;
import com.api.v1.bearerauth.model.enumerate.CommentStatus;
import com.api.v1.bearerauth.model.enumerate.NewsStatus;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.request.CommentRequest;
import com.api.v1.bearerauth.model.response.CommentDto;
import com.api.v1.bearerauth.repository.AppUserRepository;
import com.api.v1.bearerauth.repository.CommentRepository;
import com.api.v1.bearerauth.repository.NewsRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.service.CommentService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_LENGTH = 2000;
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final CommentRepository commentRepository;
    private final NewsRepository newsRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;

    @Override
    @Transactional
    public Page<CommentDto> listVisible(Long newsId, int page, int size) {
        publishedNews(newsId);
        return commentRepository.findByNewsIdAndStatus(newsId, CommentStatus.VISIBLE, pageable(page, size)).map(this::toDto);
    }

    @Override
    @Transactional
    public CommentDto create(Long newsId, CommentRequest request) {
        AppUser user = currentUserService.getCurrentUser();
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty() || content.length() > MAX_LENGTH) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.INVALID_PARAMETER.getDefaultMessage());
        }
        News news = publishedNews(newsId);
        if (!news.isCommentsEnabled()) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.COMMENTS_DISABLED.getDefaultMessage());
        }

        Comment comment = new Comment();
        comment.setNews(news);
        comment.setUser(appUserRepository.getReferenceById(user.getId()));
        comment.setContent(content);
        comment.setStatus(CommentStatus.VISIBLE);
        Comment saved = commentRepository.save(comment);
        return toDto(saved, user);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AppUser user = currentUserService.getCurrentUser();
        Comment comment = find(id);
        boolean own = comment.getUser().getId().equals(user.getId());
        if (!own && !isStaff(user)) {
            throw new CustomException(HttpStatus.FORBIDDEN, MessageConstant.INSUFFICIENT_PERMISSIONS.getDefaultMessage());
        }
        commentRepository.delete(comment);
    }

    @Override
    @Transactional
    public Page<CommentDto> listAll(CommentStatus status, Long newsId, Long userId, int page, int size) {
        requireStaff();
        return commentRepository.search(status, newsId, userId, pageable(page, size)).map(this::toDto);
    }

    @Override
    @Transactional
    public CommentDto hide(Long id) {
        return setStatus(id, CommentStatus.HIDDEN);
    }

    @Override
    @Transactional
    public CommentDto unhide(Long id) {
        return setStatus(id, CommentStatus.VISIBLE);
    }

    private CommentDto setStatus(Long id, CommentStatus status) {
        requireStaff();
        Comment comment = find(id);
        if (comment.getStatus() != status) {
            comment.setStatus(status);
            comment = commentRepository.save(comment);
        }
        return toDto(comment);
    }

    // ---- rules ----

    // readers only know published articles (in an active category, like the public article page)
    private News publishedNews(Long newsId) {
        News news = newsRepository.findById(newsId)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.NEWS_NOT_FOUND.getDefaultMessage()));
        if (news.getStatus() != NewsStatus.PUBLISHED || !news.getCategory().isActive()) {
            throw new CustomException(HttpStatus.NOT_FOUND, MessageConstant.NEWS_NOT_FOUND.getDefaultMessage());
        }
        return news;
    }

    private Comment find(Long id) {
        return commentRepository.findWithUserAndNewsById(id)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.COMMENT_NOT_FOUND.getDefaultMessage()));
    }

    private boolean isStaff(AppUser user) {
        return user.getRole() == Roles.ADMIN || user.getRole() == Roles.EDITOR;
    }

    private void requireStaff() {
        if (!isStaff(currentUserService.getCurrentUser())) {
            throw new CustomException(HttpStatus.FORBIDDEN, MessageConstant.INSUFFICIENT_PERMISSIONS.getDefaultMessage());
        }
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), MAX_PAGE_SIZE), NEWEST);
    }

    // ---- mapping ----

    private CommentDto toDto(Comment comment) {
        return toDto(comment, comment.getUser());
    }

    private CommentDto toDto(Comment comment, AppUser user) {
        News news = comment.getNews();
        return new CommentDto(
                comment.getId(),
                news.getId(),
                news.getTitle(),
                news.getSlug(),
                user.getId(),
                user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getName(),
                comment.getContent(),
                comment.getStatus(),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}
