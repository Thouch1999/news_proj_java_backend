package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Notification;
import com.api.v1.newssystem.model.enumerate.NotificationType;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.response.NotificationDto;
import com.api.v1.newssystem.service.NotificationEvent;
import com.api.v1.newssystem.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The notification queries and rules against the configured MySQL. record() writes in its own transaction (as after a
 * commit), so these tests commit their rows and delete them again; every user carries a unique token.
 */
@SpringBootTest
class NotificationQueryTest {
    private static final long NEWS_ID = 4242L;

    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRepository notificationRepository;

    private String token;
    private AppUser author;
    private AppUser reader;
    private AppUser other;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString().substring(0, 8);
        author = user("author", Roles.AUTHOR);
        reader = user("reader", Roles.READER);
        other = user("other", Roles.READER);
        loginAs(author);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        Set<Long> ids = Set.of(author.getId(), reader.getId(), other.getId());
        notificationRepository.deleteAll(notificationRepository.findAll().stream()
                .filter(n -> ids.contains(n.getRecipient().getId())).toList());
        appUserRepository.deleteAllById(ids);
    }

    private AppUser user(String name, Roles role) {
        AppUser user = new AppUser();
        user.setEmail(name + "-" + token + "@news.local");
        user.setPassword("x");
        user.setRole(role);
        user.setUsername(name + " " + token);
        return appUserRepository.save(user);
    }

    private void loginAs(AppUser user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private NotificationEvent comment(AppUser commenter, AppUser recipient) {
        return new NotificationEvent(NotificationType.NEW_COMMENT, commenter.getId(), recipient.getId(), NEWS_ID, null,
                Map.of("newsTitle", "Title", "newsSlug", "title-" + token, "actorName", commenter.getName()));
    }

    private Notification stored(AppUser recipient, LocalDateTime readAt, LocalDateTime updatedAt) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(NotificationType.ARTICLE_PUBLISHED);
        notification.setReadAt(readAt);
        notification.setCreatedAt(updatedAt);
        notification.setUpdatedAt(updatedAt);
        return notificationRepository.saveAndFlush(notification);
    }

    @Test
    void newComment_groupsIntoTheUnreadRow_andStartsANewRowAfterItWasRead() {
        notificationService.record(comment(reader, author));
        notificationService.record(comment(other, author));

        List<NotificationDto> rows = notificationService.list(false, 1, 10).getContent();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getGroupCount()).isEqualTo(2);
        assertThat(rows.get(0).getData()).containsEntry("actorName", other.getName()).containsEntry("count", 2);
        assertThat(rows.get(0).getLink()).isEqualTo("/author/articles/" + NEWS_ID);

        notificationService.markRead(rows.get(0).getId());
        notificationService.record(comment(reader, author));

        List<NotificationDto> after = notificationService.list(false, 1, 10).getContent();
        assertThat(after).hasSize(2);
        assertThat(after.get(0).getGroupCount()).isEqualTo(1); // the new one is the newest activity
        assertThat(after.get(0).isRead()).isFalse();
        assertThat(after.get(1).getGroupCount()).isEqualTo(2);
        assertThat(after.get(1).isRead()).isTrue();
        assertThat(notificationService.list(true, 1, 10).getContent()).hasSize(1);
        assertThat(notificationService.unreadCount()).isEqualTo(1);
    }

    @Test
    void aGroupIsPerRecipientAndPerArticle() {
        notificationService.record(comment(reader, author));
        notificationService.record(new NotificationEvent(NotificationType.NEW_COMMENT, reader.getId(), author.getId(),
                NEWS_ID + 1, null, Map.of("actorName", "x")));
        notificationService.record(comment(other, reader));

        assertThat(notificationService.list(false, 1, 10).getContent()).hasSize(2); // two articles, not grouped together
        loginAs(reader);
        assertThat(notificationService.list(false, 1, 10).getContent()).hasSize(1);
    }

    @Test
    void aUser_onlySeesCountsMarksAndDeletes_theirOwnNotifications() {
        notificationService.record(comment(reader, author));
        Notification others = stored(other, null, LocalDateTime.now());
        Long mine = notificationService.list(false, 1, 10).getContent().get(0).getId();

        // the author sees one, the stranger's row is invisible and untouchable
        assertThat(notificationService.unreadCount()).isEqualTo(1);
        assertThatThrownBy(() -> notificationService.markRead(others.getId())).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> notificationService.delete(others.getId())).isInstanceOf(CustomException.class);

        loginAs(reader);
        assertThatThrownBy(() -> notificationService.markRead(mine)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> notificationService.delete(mine)).isInstanceOf(CustomException.class);
        notificationService.markAllRead(); // the reader has none: changes nobody else's rows
        assertThat(notificationRepository.findById(mine)).get().satisfies(n -> assertThat(n.getReadAt()).isNull());
        assertThat(notificationRepository.findById(others.getId())).get().satisfies(n -> assertThat(n.getReadAt()).isNull());
    }

    @Test
    void markAllRead_andDelete_workOnOwnRows() {
        notificationService.record(comment(reader, author));
        notificationService.record(new NotificationEvent(NotificationType.ARTICLE_PUBLISHED, other.getId(), author.getId(),
                NEWS_ID, null, Map.of("newsTitle", "T", "newsSlug", "t")));
        assertThat(notificationService.unreadCount()).isEqualTo(2);

        notificationService.markAllRead();
        assertThat(notificationService.unreadCount()).isZero();

        Long id = notificationService.list(false, 1, 10).getContent().get(0).getId();
        notificationService.delete(id);
        assertThat(notificationService.list(false, 1, 10).getContent()).hasSize(1);
    }

    @Test
    void cleanup_deletesOnlyOldReadNotifications() {
        LocalDateTime old = LocalDateTime.now().minusDays(120);
        LocalDateTime recent = LocalDateTime.now().minusDays(10);
        Notification oldRead = stored(author, old.plusDays(1), old);
        Notification oldUnread = stored(author, null, old);
        Notification recentRead = stored(author, recent, recent);
        Notification recentUnread = stored(author, null, recent);

        notificationService.deleteOldRead();

        assertThat(notificationRepository.findById(oldRead.getId())).isEmpty();
        assertThat(notificationRepository.findById(oldUnread.getId())).isPresent();
        assertThat(notificationRepository.findById(recentRead.getId())).isPresent();
        assertThat(notificationRepository.findById(recentUnread.getId())).isPresent();
    }
}
