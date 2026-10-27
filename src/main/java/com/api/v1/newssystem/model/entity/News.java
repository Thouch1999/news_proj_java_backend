package com.api.v1.newssystem.model.entity;

import com.api.v1.newssystem.model.enumerate.NewsStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@NullMarked
@Table(name = "news", options = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        indexes = {
                @Index(name = "idx_news_status", columnList = "status"),
                @Index(name = "idx_news_category_id", columnList = "category_id"),
                @Index(name = "idx_news_published_at", columnList = "published_at"),
                @Index(name = "idx_news_author_id", columnList = "author_id"),
                @Index(name = "idx_news_status_scheduled_at", columnList = "status, scheduled_at")
        })
public class News {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(length = 500)
    private String thumbnail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private NewsStatus status = NewsStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // scheduled publishing: when (UTC) and by whom; set while the article is SCHEDULED, kept after the job published it
    @Column(name = "scheduled_at")
    @Nullable
    private Instant scheduledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheduled_by")
    @Nullable
    private AppUser scheduledBy;

    @Column(nullable = false)
    private long viewCount = 0;

    @Column(nullable = false)
    private boolean commentsEnabled = true;

    @Column(length = 1000)
    private String rejectionReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
