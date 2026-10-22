package com.api.v1.bearerauth.model.entity;

import com.api.v1.bearerauth.model.enumerate.ActivityAction;
import com.api.v1.bearerauth.model.enumerate.ActivityTargetType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * One entry of the activity log. It is written once and never changed: no setters, Hibernate ignores updates
 * (@Immutable), every column is not updatable, and the repository offers no update or delete (only the retention job
 * deletes old entries). The actor, the roles and the target label are snapshots, so an entry stays readable after the user
 * or the article is renamed or gone; actor_id has no foreign key, so it never blocks the deletion of a user.
 */
@Entity
@Immutable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "activity_logs", options = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        indexes = {
                @Index(name = "idx_activity_created", columnList = "created_at"),
                @Index(name = "idx_activity_actor_created", columnList = "actor_id, created_at"),
                @Index(name = "idx_activity_target_created", columnList = "target_type, target_id, created_at"),
                @Index(name = "idx_activity_action_created", columnList = "action, created_at")
        })
public class ActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // null for a system job or a failed login
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private AppUser actor;

    @Column(length = 150, updatable = false)
    private String actorName;

    @Column(length = 100, updatable = false)
    private String actorRoles;

    @Column(length = 45, updatable = false)
    private String ipAddress;

    @Column(length = 200, updatable = false)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "VARCHAR(40)")
    private ActivityAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "VARCHAR(20)")
    private ActivityTargetType targetType;

    @Column(updatable = false)
    private Long targetId;

    @Column(length = 255, updatable = false)
    private String targetLabel;

    // JSON text: what changed (see ActivityLogService); never a secret
    @Column(updatable = false, columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false, updatable = false, columnDefinition = "datetime(6) not null default current_timestamp(6)")
    private LocalDateTime createdAt;
}
