package com.api.v1.newssystem.service.impl;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Who receives which notification, no self-notification, the NEW_COMMENT grouping, the links and the cleanup. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {
    private static final long AUTHOR = 1L;
    private static final long EDITOR = 2L;
    private static final long ADMIN = 3L;
    private static final long READER = 4L;
    private static final long NEWS_ID = 10L;

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private CurrentUserService currentUserService;

    private final AppProperty appProperty = new AppProperty();
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, appUserRepository, currentUserService,
                new ObjectMapper(), appProperty);
        lenient().when(appUserRepository.getReferenceById(anyLong())).thenAnswer(call -> user(call.getArgument(0), Roles.READER));
        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(call -> call.getArgument(0));
    }

    private AppUser user(long id, Roles role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        user.setUsername("user" + id);
        return user;
    }

    private void loginAs(AppUser user) {
        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    private NotificationEvent event(NotificationType type, long actor, Long recipient) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("newsTitle", "Title");
        data.put("newsSlug", "title");
        data.put("actorName", "user" + actor);
        return new NotificationEvent(type, actor, recipient, NEWS_ID, null, data);
    }

    private List<Notification> saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private Set<Long> recipientIds(List<Notification> rows) {
        return rows.stream().map(n -> n.getRecipient().getId()).collect(Collectors.toSet());
    }

    // ---------------- recipients ----------------

    @Test
    void submitted_goesToEveryActiveEditorAndAdmin_exceptTheActor() {
        when(appUserRepository.findByRoleInAndActiveTrue(List.of(Roles.EDITOR, Roles.ADMIN)))
                .thenReturn(List.of(user(EDITOR, Roles.EDITOR), user(ADMIN, Roles.ADMIN)));

        service.record(event(NotificationType.ARTICLE_SUBMITTED, AUTHOR, null));

        List<Notification> rows = saved();
        assertThat(recipientIds(rows)).containsExactlyInAnyOrder(EDITOR, ADMIN);
        assertThat(rows).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.ARTICLE_SUBMITTED);
            assertThat(n.getActor().getId()).isEqualTo(AUTHOR);
            assertThat(n.getNewsId()).isEqualTo(NEWS_ID);
            assertThat(n.getGroupCount()).isEqualTo(1);
            assertThat(n.getTitle()).isNotBlank();
            assertThat(n.getMessage()).contains("Title");
            assertThat(n.getData()).contains("\"newsSlug\":\"title\"");
        });
    }

    @Test
    void submitted_byAnAdmin_doesNotNotifyThatAdmin() {
        when(appUserRepository.findByRoleInAndActiveTrue(any()))
                .thenReturn(List.of(user(EDITOR, Roles.EDITOR), user(ADMIN, Roles.ADMIN)));

        service.record(event(NotificationType.ARTICLE_SUBMITTED, ADMIN, null));

        assertThat(recipientIds(saved())).containsExactly(EDITOR);
    }

    @Test
    void newUser_goesToTheAdminsOnly() {
        when(appUserRepository.findByRoleInAndActiveTrue(List.of(Roles.ADMIN))).thenReturn(List.of(user(ADMIN, Roles.ADMIN)));
        Map<String, Object> data = Map.of("actorName", "New Reader", "userId", READER);

        service.record(new NotificationEvent(NotificationType.NEW_USER, READER, null, null, null, data));

        List<Notification> rows = saved();
        assertThat(recipientIds(rows)).containsExactly(ADMIN);
        assertThat(rows.get(0).getActor().getId()).isEqualTo(READER);
        verify(appUserRepository, never()).findByRoleInAndActiveTrue(List.of(Roles.EDITOR, Roles.ADMIN));
    }

    @Test
    void decisionsAndRoleChange_goToTheNamedRecipientOnly() {
        for (NotificationType type : List.of(NotificationType.ARTICLE_PUBLISHED, NotificationType.ARTICLE_REJECTED,
                NotificationType.ARTICLE_ARCHIVED, NotificationType.COMMENT_HIDDEN, NotificationType.ROLES_CHANGED)) {
            org.mockito.Mockito.clearInvocations(notificationRepository);

            service.record(event(type, EDITOR, AUTHOR));

            assertThat(recipientIds(saved())).as(type.name()).containsExactly(AUTHOR);
        }
        verify(appUserRepository, never()).findByRoleInAndActiveTrue(any());
    }

    @Test
    void noSelfNotification_whenTheActorIsTheRecipient() {
        for (NotificationType type : List.of(NotificationType.ARTICLE_PUBLISHED, NotificationType.NEW_COMMENT,
                NotificationType.COMMENT_HIDDEN, NotificationType.ROLES_CHANGED)) {
            service.record(event(type, AUTHOR, AUTHOR));
        }

        verify(notificationRepository, never()).save(any());
    }

    // ---------------- grouping of NEW_COMMENT ----------------

    @Test
    void newComment_withoutAnUnreadOne_createsARow_withCountOne() {
        when(notificationRepository.findFirstByRecipientIdAndTypeAndNewsIdAndReadAtIsNullOrderByIdDesc(
                AUTHOR, NotificationType.NEW_COMMENT, NEWS_ID)).thenReturn(Optional.empty());

        service.record(event(NotificationType.NEW_COMMENT, READER, AUTHOR));

        Notification row = saved().get(0);
        assertThat(row.getGroupCount()).isEqualTo(1);
        assertThat(row.getData()).contains("\"count\":1").contains("\"actorName\":\"user4\"");
    }

    @Test
    void newComment_withAnUnreadOne_growsIt_withTheLatestCommenter_andNoNewRow() {
        Notification open = new Notification();
        open.setRecipient(user(AUTHOR, Roles.AUTHOR));
        open.setType(NotificationType.NEW_COMMENT);
        open.setNewsId(NEWS_ID);
        open.setGroupCount(1);
        open.setData("{\"newsTitle\":\"Title\",\"newsSlug\":\"title\",\"actorName\":\"first\",\"count\":1}");
        LocalDateTime before = LocalDateTime.now().minusHours(1);
        open.setUpdatedAt(before);
        when(notificationRepository.findFirstByRecipientIdAndTypeAndNewsIdAndReadAtIsNullOrderByIdDesc(
                AUTHOR, NotificationType.NEW_COMMENT, NEWS_ID)).thenReturn(Optional.of(open));

        service.record(event(NotificationType.NEW_COMMENT, READER, AUTHOR));

        verify(notificationRepository, times(1)).save(open);
        assertThat(open.getGroupCount()).isEqualTo(2);
        assertThat(open.getData()).contains("\"actorName\":\"user4\"").contains("\"count\":2");
        assertThat(open.getActor().getId()).isEqualTo(READER);
        assertThat(open.getUpdatedAt()).isAfter(before);
        assertThat(open.getReadAt()).isNull();
        assertThat(open.getMessage()).contains("user4 and 1 other");
    }

    // ---------------- my notifications ----------------

    @Test
    void anotherUsersNotification_cannotBeReadMarkedOrDeleted() {
        loginAs(user(AUTHOR, Roles.AUTHOR));
        when(notificationRepository.findByIdAndRecipientId(77L, AUTHOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(77L)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.delete(77L)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(notificationRepository, never()).save(any());
        verify(notificationRepository, never()).delete(any(Notification.class));
    }

    @Test
    void list_asksOnlyForTheLoggedInUsersRows_andBuildsTheLinkFromTheirRole() {
        Notification submitted = new Notification();
        submitted.setId(5L);
        submitted.setType(NotificationType.ARTICLE_SUBMITTED);
        submitted.setRecipient(user(ADMIN, Roles.ADMIN));
        submitted.setNewsId(NEWS_ID);
        submitted.setData("{\"newsSlug\":\"title\"}");
        for (Object[] row : new Object[][]{
                {Roles.ADMIN, "/admin/review/10"}, {Roles.EDITOR, "/editor/review/10"},
                {Roles.AUTHOR, "/author/articles/10"}, {Roles.READER, "/news/title"}}) {
            loginAs(user(ADMIN, (Roles) row[0]));
            when(notificationRepository.findMine(eq(ADMIN), eq(true), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(submitted)));

            NotificationDto dto = service.list(true, 1, 10).getContent().get(0);

            assertThat(dto.getLink()).isEqualTo(row[1]);
            assertThat(dto.isRead()).isFalse();
            assertThat(dto.getData()).containsEntry("newsSlug", "title");
        }
    }

    @Test
    void links_ofNewUser_andOfARoleChange() {
        loginAs(user(ADMIN, Roles.ADMIN));
        Notification newUser = new Notification();
        newUser.setType(NotificationType.NEW_USER);
        newUser.setRecipient(user(ADMIN, Roles.ADMIN));
        newUser.setActor(user(READER, Roles.READER));
        Notification roles = new Notification();
        roles.setType(NotificationType.ROLES_CHANGED);
        roles.setRecipient(user(ADMIN, Roles.ADMIN));
        when(notificationRepository.findMine(eq(ADMIN), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(newUser, roles)));

        List<NotificationDto> dtos = service.list(false, 1, 10).getContent();

        assertThat(dtos.get(0).getLink()).isEqualTo("/admin/users/4");
        assertThat(dtos.get(1).getLink()).isNull();
    }

    // ---------------- cleanup ----------------

    @Test
    void deleteOldRead_usesTheConfiguredRetention() {
        appProperty.getNotification().setRetentionDays(30);
        when(notificationRepository.deleteReadOlderThan(any(LocalDateTime.class))).thenReturn(4);

        assertThat(service.deleteOldRead()).isEqualTo(4);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationRepository).deleteReadOlderThan(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(LocalDateTime.now().minusDays(30).minusMinutes(1),
                LocalDateTime.now().minusDays(30).plusMinutes(1));
    }
}
