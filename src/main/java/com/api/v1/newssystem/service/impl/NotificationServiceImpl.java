package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Notification;
import com.api.v1.newssystem.model.enumerate.NotificationType;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.response.NotificationDto;
import com.api.v1.newssystem.repository.AppUserRepository;
import com.api.v1.newssystem.repository.NotificationRepository;
import com.api.v1.newssystem.security.CurrentUserService;
import com.api.v1.newssystem.service.NotificationEvent;
import com.api.v1.newssystem.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
    private static final TypeReference<Map<String, Object>> DATA_TYPE = new TypeReference<>() { };

    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final AppProperty appProperty;

    // ---- creating ----

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void record(NotificationEvent event) {
        for (Long recipientId : recipients(event)) {
            if (event.type() == NotificationType.NEW_COMMENT && event.newsId() != null && groupComment(recipientId, event)) {
                continue;
            }
            Notification notification = new Notification();
            notification.setRecipient(appUserRepository.getReferenceById(recipientId));
            notification.setType(event.type());
            notification.setActor(event.actorId() == null ? null : appUserRepository.getReferenceById(event.actorId()));
            notification.setNewsId(event.newsId());
            notification.setCommentId(event.commentId());
            Map<String, Object> data = new LinkedHashMap<>(event.data());
            if (event.type() == NotificationType.NEW_COMMENT) {
                data.put("count", 1);
            }
            notification.setData(toJson(data));
            notification.setTitle(title(event.type()));
            notification.setMessage(message(event.type(), data, 1));
            notificationRepository.save(notification);
        }
    }

    // never the user who did the action; the staff events go to every active user of the group
    private List<Long> recipients(NotificationEvent event) {
        List<Long> ids = switch (event.type()) {
            case ARTICLE_SUBMITTED -> staffIds(List.of(Roles.EDITOR, Roles.ADMIN));
            case NEW_USER -> staffIds(List.of(Roles.ADMIN));
            default -> event.recipientId() == null ? List.of() : List.of(event.recipientId());
        };
        return ids.stream().filter(id -> !Objects.equals(id, event.actorId())).toList();
    }

    private List<Long> staffIds(List<Roles> roles) {
        return appUserRepository.findByRoleInAndActiveTrue(roles).stream().map(AppUser::getId).toList();
    }

    // an UNREAD NEW_COMMENT of the same article grows instead of a new row: count, latest commenter, last activity
    private boolean groupComment(Long recipientId, NotificationEvent event) {
        Notification open = notificationRepository
                .findFirstByRecipientIdAndTypeAndNewsIdAndReadAtIsNullOrderByIdDesc(recipientId, event.type(), event.newsId())
                .orElse(null);
        if (open == null) {
            return false;
        }
        open.setGroupCount(open.getGroupCount() + 1);
        open.setActor(event.actorId() == null ? null : appUserRepository.getReferenceById(event.actorId()));
        open.setCommentId(event.commentId());
        open.setUpdatedAt(LocalDateTime.now());
        Map<String, Object> data = fromJson(open.getData());
        data.putAll(event.data()); // the latest commenter (actorName)
        data.put("count", open.getGroupCount());
        open.setData(toJson(data));
        open.setMessage(message(event.type(), data, open.getGroupCount()));
        notificationRepository.save(open);
        return true;
    }

    // ---- reading and changing my notifications (a user only ever reaches their own) ----

    @Override
    @Transactional
    public Page<NotificationDto> list(boolean unreadOnly, int page, int size) {
        AppUser user = currentUserService.getCurrentUser();
        PageRequest pageable = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), MAX_PAGE_SIZE), NEWEST);
        return notificationRepository.findMine(user.getId(), unreadOnly, pageable).map(n -> toDto(n, user.getRole()));
    }

    @Override
    @Transactional
    public long unreadCount() {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(currentUserService.getCurrentUser().getId());
    }

    @Override
    @Transactional
    public NotificationDto markRead(Long id) {
        AppUser user = currentUserService.getCurrentUser();
        Notification notification = findMine(id, user);
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return toDto(notification, user.getRole());
    }

    @Override
    @Transactional
    public void markAllRead() {
        notificationRepository.markAllRead(currentUserService.getCurrentUser().getId(), LocalDateTime.now());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        notificationRepository.delete(findMine(id, currentUserService.getCurrentUser()));
    }

    // somebody else's notification is "not found", like one that does not exist
    private Notification findMine(Long id, AppUser user) {
        return notificationRepository.findByIdAndRecipientId(id, user.getId())
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.NOTIFICATION_NOT_FOUND.getDefaultMessage()));
    }

    @Override
    @Transactional
    public int deleteOldRead() {
        return notificationRepository.deleteReadOlderThan(
                LocalDateTime.now().minusDays(appProperty.getNotification().getRetentionDays()));
    }

    // ---- mapping ----

    private NotificationDto toDto(Notification n, Roles role) {
        Map<String, Object> data = fromJson(n.getData());
        return new NotificationDto(n.getId(), n.getType(), data, n.getGroupCount(), n.getReadAt() != null,
                n.getTitle(), n.getMessage(), link(n, role, data), n.getCreatedAt(), n.getUpdatedAt());
    }

    // the frontend path to open: the dashboard article page for staff, the public article page for readers,
    // the user page for NEW_USER (admins); none for a change of roles
    private String link(Notification n, Roles role, Map<String, Object> data) {
        if (n.getType() == NotificationType.NEW_USER) {
            return n.getActor() == null ? null : "/admin/users/" + n.getActor().getId();
        }
        if (n.getNewsId() == null) {
            return null;
        }
        Object slug = data.get("newsSlug");
        return switch (role) {
            case READER -> slug == null ? null : "/news/" + slug;
            case AUTHOR -> "/author/articles/" + n.getNewsId();
            case EDITOR -> (n.getType() == NotificationType.ARTICLE_SUBMITTED ? "/editor/review/" : "/editor/news/") + n.getNewsId();
            case ADMIN -> (n.getType() == NotificationType.ARTICLE_SUBMITTED ? "/admin/review/" : "/admin/news/") + n.getNewsId();
        };
    }

    // ---- fallback text and JSON ----

    private static String title(NotificationType type) {
        return switch (type) {
            case ARTICLE_SUBMITTED -> "Article submitted for review";
            case ARTICLE_SCHEDULED -> "Article scheduled";
            case ARTICLE_PUBLISHED -> "Article published";
            case ARTICLE_REJECTED -> "Article rejected";
            case ARTICLE_ARCHIVED -> "Article archived";
            case NEW_COMMENT -> "New comment";
            case COMMENT_HIDDEN -> "Comment hidden";
            case NEW_USER -> "New user";
            case ROLES_CHANGED -> "Your role changed";
        };
    }

    private static String message(NotificationType type, Map<String, Object> data, int count) {
        String actor = String.valueOf(data.getOrDefault("actorName", "Someone"));
        String news = "\"" + data.getOrDefault("newsTitle", "") + "\"";
        return switch (type) {
            case ARTICLE_SUBMITTED -> actor + " submitted " + news + " for review";
            case ARTICLE_SCHEDULED -> news + " is scheduled for " + data.getOrDefault("scheduledAt", "");
            case ARTICLE_PUBLISHED -> news + " was published";
            case ARTICLE_REJECTED -> news + " was rejected" + (data.get("reason") == null ? "" : ": " + data.get("reason"));
            case ARTICLE_ARCHIVED -> news + " was archived";
            case NEW_COMMENT -> count > 1
                    ? actor + " and " + (count - 1) + " other" + (count > 2 ? "s" : "") + " commented on " + news
                    : actor + " commented on " + news;
            case COMMENT_HIDDEN -> "Your comment on " + news + " was hidden";
            case NEW_USER -> actor + " joined";
            case ROLES_CHANGED -> "Your role is now " + data.getOrDefault("roles", "");
        };
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Notification data is not valid JSON", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, DATA_TYPE);
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }
}
