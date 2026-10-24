package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.Notification;
import com.api.v1.newssystem.model.enumerate.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("""
            select n from Notification n
            where n.recipient.id = :recipientId and (:unreadOnly = false or n.readAt is null)
            """)
    Page<Notification> findMine(@Param("recipientId") Long recipientId, @Param("unreadOnly") boolean unreadOnly, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    // the unread notification a new one of the same kind is grouped into
    Optional<Notification> findFirstByRecipientIdAndTypeAndNewsIdAndReadAtIsNullOrderByIdDesc(
            Long recipientId, NotificationType type, Long newsId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.recipient.id = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.readAt is not null and n.updatedAt < :cutoff")
    int deleteReadOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
