package com.api.v1.newssystem.service;

import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Comment;
import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.enumerate.NotificationType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Published by the services inside their transaction; the notification is written after it commits
 * (see NotificationEventListener). Holds ids and plain values only. recipientId is null for the events that go to a
 * group of staff (ARTICLE_SUBMITTED to editors and admins, NEW_USER to admins).
 */
public record NotificationEvent(NotificationType type, Long actorId, Long recipientId, Long newsId, Long commentId,
                                Map<String, Object> data) {

    public static String nameOf(AppUser user) {
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getName();
    }

    public static NotificationEvent articleSubmitted(AppUser actor, News news) {
        return new NotificationEvent(NotificationType.ARTICLE_SUBMITTED, actor.getId(), null, news.getId(), null,
                articleData(news, actor));
    }

    // PUBLISHED, REJECTED (with the reason) and ARCHIVED go to the author of the article
    public static NotificationEvent articleDecision(NotificationType type, AppUser actor, News news, String reason) {
        Map<String, Object> data = articleData(news, actor);
        if (reason != null) {
            data.put("reason", reason);
        }
        return new NotificationEvent(type, actor.getId(), news.getAuthor().getId(), news.getId(), null, data);
    }

    public static NotificationEvent newComment(AppUser actor, News news, Comment comment) {
        return new NotificationEvent(NotificationType.NEW_COMMENT, actor.getId(), news.getAuthor().getId(), news.getId(),
                comment.getId(), articleData(news, actor));
    }

    public static NotificationEvent commentHidden(AppUser actor, News news, Comment comment) {
        return new NotificationEvent(NotificationType.COMMENT_HIDDEN, actor.getId(), comment.getUser().getId(), news.getId(),
                comment.getId(), articleData(news, actor));
    }

    public static NotificationEvent newUser(AppUser newUser) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("actorName", nameOf(newUser));
        data.put("userId", newUser.getId());
        return new NotificationEvent(NotificationType.NEW_USER, newUser.getId(), null, null, null, data);
    }

    public static NotificationEvent rolesChanged(AppUser actor, AppUser target, List<String> roles, List<String> previousRoles) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("actorName", nameOf(actor));
        data.put("roles", roles);
        data.put("previousRoles", previousRoles);
        return new NotificationEvent(NotificationType.ROLES_CHANGED, actor.getId(), target.getId(), null, null, data);
    }

    private static Map<String, Object> articleData(News news, AppUser actor) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("newsTitle", news.getTitle());
        data.put("newsSlug", news.getSlug());
        data.put("actorName", nameOf(actor));
        return data;
    }
}
