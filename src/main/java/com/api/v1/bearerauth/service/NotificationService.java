package com.api.v1.bearerauth.service;

import com.api.v1.bearerauth.model.response.NotificationDto;
import org.springframework.data.domain.Page;

public interface NotificationService {
    // writes the notification(s) of an event (own transaction; called after the main one committed)
    void record(NotificationEvent event);

    // the logged-in user's own notifications, newest activity first; unreadOnly = false lists all
    Page<NotificationDto> list(boolean unreadOnly, int page, int size);

    long unreadCount();

    NotificationDto markRead(Long id);

    void markAllRead();

    void delete(Long id);

    // deletes the read notifications older than app.notification.retention-days; returns how many
    int deleteOldRead();
}
