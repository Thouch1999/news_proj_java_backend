package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.ActivityTargetType;
import com.api.v1.newssystem.service.ActivityLogService;
import com.api.v1.newssystem.util.ActivityDetails;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Comment;
import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.enumerate.CommentStatus;
import com.api.v1.newssystem.model.enumerate.NewsStatus;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.request.CommentRequest;
import com.api.v1.newssystem.model.response.CommentDto;
import com.api.v1.newssystem.repository.AppUserRepository;
import com.api.v1.newssystem.repository.CommentRepository;
import com.api.v1.newssystem.repository.NewsRepository;
import com.api.v1.newssystem.security.CurrentUserService;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.model.enumerate.RateLimitAction;
import com.api.v1.newssystem.service.CommentService;
import com.api.v1.newssystem.service.RateLimitService;
import com.api.v1.newssystem.service.SystemSettingService;
import com.api.v1.newssystem.util.CommentRules;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.api.v1.newssystem.service.NotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
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
    private final ApplicationEventPublisher eventPublisher;
    private final ActivityLogService activityLogService;
    private final RateLimitService rateLimitService;
    private final SystemSettingService systemSettingService;
    private final AppProperty appProperty;
    private final Clock clock;

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
        if (request.getWebsite() != null && !request.getWebsite().isBlank()) {
            // honeypot: a bot filled the hidden field. It is told that it worked, and nothing is saved.
            log.debug("Honeypot: comment of user {} ignored", user.getId());
            return fakeComment(content, user);
        }
        rateLimitService.checkUser(RateLimitAction.COMMENT_PER_MINUTE, user.getId());
        rateLimitService.checkUser(RateLimitAction.COMMENT_PER_DAY, user.getId());
        if (content.isEmpty() || content.length() > MAX_LENGTH) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.INVALID_PARAMETER.getDefaultMessage());
        }
        News news = publishedNews(newsId);
        if (!news.isCommentsEnabled()) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.COMMENTS_DISABLED.getDefaultMessage());
        }
        requireOldEnoughAccount(user);
        if (CommentRules.countLinks(content) > appProperty.getProtection().getComment().getMaxLinks()) {
            throw new CustomException(MessageConstant.COMMENT_TOO_MANY_LINKS);
        }
        boolean duplicate = commentRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                .filter(previous -> CommentRules.isSameText(previous.getContent(), content))
                .isPresent();
        if (duplicate) {
            throw new CustomException(MessageConstant.COMMENT_DUPLICATE);
        }

        // a blocked word does not refuse the comment: it is saved HIDDEN for a moderator, who sees the reason
        String blockedWord = CommentRules.findBlockedWord(content, systemSettingService.getCommentBlockedWords());

        Comment comment = new Comment();
        comment.setNews(news);
        comment.setUser(appUserRepository.getReferenceById(user.getId()));
        comment.setContent(content);
        comment.setStatus(blockedWord == null ? CommentStatus.VISIBLE : CommentStatus.HIDDEN);
        comment.setModerationNote(blockedWord == null ? null : "Contains a blocked word: " + blockedWord);
        Comment saved = commentRepository.save(comment);
        if (blockedWord != null) {
            // the commenter is told it waits; the word is not revealed to them, and nobody is notified of a hidden comment
            return toDto(saved, user, false, MessageConstant.COMMENT_AWAITING_REVIEW.getDefaultMessage());
        }
        eventPublisher.publishEvent(NotificationEvent.newComment(user, news, saved)); // to the article's author, not to themselves
        return toDto(saved, user, false, null);
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
        activityLogService.record(ActivityAction.COMMENT_DELETED, ActivityTargetType.COMMENT, id, comment.getNews().getTitle(),
                ActivityDetails.of("newsId", comment.getNews().getId(), "commentAuthorId", comment.getUser().getId(), "ownComment", own));
    }

    @Override
    @Transactional
    public Page<CommentDto> listAll(CommentStatus status, Long newsId, Long userId, int page, int size) {
        requireStaff();
        return commentRepository.search(status, newsId, userId, pageable(page, size))
                .map(comment -> toDto(comment, comment.getUser(), true, null)); // moderators see the moderation note
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
        AppUser actor = requireStaff();
        Comment comment = find(id);
        if (comment.getStatus() != status) {
            comment.setStatus(status);
            comment = commentRepository.save(comment);
            activityLogService.record(status == CommentStatus.HIDDEN ? ActivityAction.COMMENT_HIDDEN : ActivityAction.COMMENT_SHOWN,
                    ActivityTargetType.COMMENT, comment.getId(), comment.getNews().getTitle(),
                    ActivityDetails.of("newsId", comment.getNews().getId(), "commentAuthorId", comment.getUser().getId()));
            if (status == CommentStatus.HIDDEN) {
                eventPublisher.publishEvent(NotificationEvent.commentHidden(actor, comment.getNews(), comment));
            }
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

    private AppUser requireStaff() {
        AppUser user = currentUserService.getCurrentUser();
        if (!isStaff(user)) {
            throw new CustomException(HttpStatus.FORBIDDEN, MessageConstant.INSUFFICIENT_PERMISSIONS.getDefaultMessage());
        }
        return user;
    }

    // an account must be a few minutes old before it can comment (a script that signs up and posts at once is stopped)
    private void requireOldEnoughAccount(AppUser user) {
        if (user.getCreatedAt() == null) {
            return;
        }
        LocalDateTime opensAt = user.getCreatedAt().plusMinutes(appProperty.getProtection().getComment().getMinAccountAgeMinutes());
        LocalDateTime now = LocalDateTime.now(clock);
        if (opensAt.isAfter(now)) {
            throw new CustomException(MessageConstant.ACCOUNT_TOO_NEW, (int) Math.max(1, (Duration.between(now, opensAt).toMillis() + 999) / 1000));
        }
    }

    // what a bot gets back: the shape of a real answer, an id that does not exist, nothing saved
    private CommentDto fakeComment(String content, AppUser user) {
        LocalDateTime now = LocalDateTime.now(clock);
        return new CommentDto(ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE), null, null, null, user.getId(),
                displayName(user), content, CommentStatus.VISIBLE, now, now, null, null);
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), MAX_PAGE_SIZE), NEWEST);
    }

    // ---- mapping ----

    private CommentDto toDto(Comment comment) {
        return toDto(comment, comment.getUser(), false, null);
    }

    private static String displayName(AppUser user) {
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getName();
    }

    private CommentDto toDto(Comment comment, AppUser user, boolean withModerationNote, String message) {
        News news = comment.getNews();
        return new CommentDto(
                comment.getId(),
                news.getId(),
                news.getTitle(),
                news.getSlug(),
                user.getId(),
                displayName(user),
                comment.getContent(),
                comment.getStatus(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                withModerationNote ? comment.getModerationNote() : null,
                message);
    }
}
