package com.api.v1.bearerauth.model.entity;

import com.api.v1.bearerauth.model.enumerate.NotificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@NullMarked
@Table(name = "notifications", options = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        indexes = {
                @Index(name = "idx_notifications_recipient_read_created", columnList = "recipient_id, read_at, created_at"),
                @Index(name = "idx_notifications_recipient_group", columnList = "recipient_id, type, news_id, read_at")
        })
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private AppUser recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private NotificationType type;

    // who did the action
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    @Nullable
    private AppUser actor;

    @Column(name = "news_id")
    @Nullable
    private Long newsId;

    @Column(name = "comment_id")
    @Nullable
    private Long commentId;

    // JSON text: the values the frontend needs to build the shown message (newsTitle, newsSlug, actorName, reason, roles, count)
    @Column(columnDefinition = "TEXT")
    @Nullable
    private String data;

    // plain English fallback; the frontend builds the shown text from type + data
    @Column(length = 255)
    @Nullable
    private String title;

    @Column(length = 500)
    @Nullable
    private String message;

    @Column(name = "group_count", nullable = false, columnDefinition = "int not null default 1")
    private int groupCount = 1;

    @Column(name = "read_at")
    @Nullable
    private LocalDateTime readAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // the last activity: set on creation and when a group grows; reading never changes it (so the order stays)
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}
