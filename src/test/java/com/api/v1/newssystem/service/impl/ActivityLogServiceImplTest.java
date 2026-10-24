package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.model.entity.ActivityLog;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.ActivityTargetType;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.repository.ActivityLogRepository;
import com.api.v1.newssystem.repository.AppUserRepository;
import com.api.v1.newssystem.security.ClientIpResolver;
import com.api.v1.newssystem.security.CurrentUserService;
import com.api.v1.newssystem.util.ActivityDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What an entry holds: the snapshots, the request, the cleaned details; and that a login entry never stops a login. */
@ExtendWith(MockitoExtension.class)
class ActivityLogServiceImplTest {
    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final AppProperty property = new AppProperty();
    private ActivityLogServiceImpl service;
    private AppUser editor;

    @BeforeEach
    void setUp() {
        service = new ActivityLogServiceImpl(activityLogRepository, appUserRepository, currentUserService,
                new ClientIpResolver(property), new ObjectMapper(), property, transactionManager);
        editor = new AppUser();
        editor.setId(7L);
        editor.setUsername("editor1");
        editor.setFullName("Sok Dara");
        editor.setEmail("editor@news.local");
        editor.setRole(Roles.EDITOR);
        lenient().when(currentUserService.findCurrentUser()).thenReturn(Optional.of(editor));
        lenient().when(appUserRepository.getReferenceById(anyLong())).thenAnswer(call -> {
            AppUser reference = new AppUser();
            reference.setId(call.getArgument(0));
            return reference;
        });
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("User-Agent", "TestBrowser/1.0\r\nInjected: header");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private ActivityLog saved() {
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void record_keepsTheSnapshotsOfTheActorTheRequestAndTheTarget() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        service.record(ActivityAction.NEWS_PUBLISHED, ActivityTargetType.NEWS, 12L, "Cup final",
                ActivityDetails.of("from", "PENDING", "to", "PUBLISHED"));

        ActivityLog entry = saved();
        assertThat(entry.getActor().getId()).isEqualTo(7L);
        assertThat(entry.getActorName()).isEqualTo("Sok Dara");
        assertThat(entry.getActorRoles()).isEqualTo("EDITOR");
        assertThat(entry.getIpAddress()).isEqualTo("203.0.113.9");
        assertThat(entry.getUserAgent()).isEqualTo("TestBrowser/1.0 Injected: header"); // no line break survives
        assertThat(entry.getAction()).isEqualTo(ActivityAction.NEWS_PUBLISHED);
        assertThat(entry.getTargetType()).isEqualTo(ActivityTargetType.NEWS);
        assertThat(entry.getTargetId()).isEqualTo(12L);
        assertThat(entry.getTargetLabel()).isEqualTo("Cup final");
        assertThat(entry.getDetails()).isEqualTo("{\"from\":\"PENDING\",\"to\":\"PUBLISHED\"}");
        assertThat(entry.getCreatedAt()).isAfter(before);
    }

    @Test
    void record_withoutALoggedInUser_hasNoActor_forASystemJob() {
        when(currentUserService.findCurrentUser()).thenReturn(Optional.empty());

        service.record(ActivityAction.SETTINGS_UPDATED, ActivityTargetType.SETTINGS, null, "general", null);

        ActivityLog entry = saved();
        assertThat(entry.getActor()).isNull();
        assertThat(entry.getActorName()).isNull();
        assertThat(entry.getActorRoles()).isNull();
        assertThat(entry.getDetails()).isNull();
    }

    @Test
    void actorNameFallsBack_toTheUsername_thenTheEmail() {
        editor.setFullName(" ");
        service.record(ActivityAction.NEWS_CREATED, ActivityTargetType.NEWS, 1L, "T", null);
        assertThat(saved().getActorName()).isEqualTo("editor1");
    }

    @Test
    void record_neverKeepsSecrets_andCutsLongText() {
        service.record(ActivityAction.USER_PASSWORD_RESET, ActivityTargetType.USER, 3L, "user3", Map.of(
                "password", "Secret-123", "otp", "123456", "refreshToken", "r", "reason", "x".repeat(900), "note", "fine"));

        String details = saved().getDetails();
        assertThat(details).doesNotContain("Secret-123").doesNotContain("123456").doesNotContain("refreshToken").doesNotContain("password");
        assertThat(details).contains("\"note\":\"fine\"");
        assertThat(details.length()).isLessThan(700);
    }

    @Test
    void record_withAHugeDescription_keepsOnlyAMarker() {
        Map<String, Object> huge = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 40; i++) {
            huge.put("key" + i, "v".repeat(400));
        }

        service.record(ActivityAction.NEWS_UPDATED, ActivityTargetType.NEWS, 1L, "T", huge);

        assertThat(saved().getDetails()).isEqualTo("{\"truncated\":true}");
    }

    @Test
    void recordFor_usesTheGivenActor_notTheLoggedInUser() {
        AppUser other = new AppUser();
        other.setId(9L);
        other.setUsername("reader9");
        other.setEmail("r9@news.local");
        other.setRole(Roles.READER);

        service.recordFor(other, ActivityAction.PASSWORD_CHANGED, ActivityTargetType.AUTH, 9L, "r9@news.local", Map.of("method", "reset"));

        ActivityLog entry = saved();
        assertThat(entry.getActor().getId()).isEqualTo(9L);
        assertThat(entry.getActorName()).isEqualTo("reader9");
        assertThat(entry.getActorRoles()).isEqualTo("READER");
    }

    @Test
    void recordLogin_withAFailingTransaction_doesNotThrow_andWritesNothing() {
        when(transactionManager.getTransaction(any())).thenThrow(new IllegalStateException("database down"));

        service.recordLogin(ActivityAction.LOGIN_FAILED, null, "typed@news.local", Map.of("reason", "invalid credentials"));

        verify(activityLogRepository, never()).save(any());
    }

    @Test
    void recordLogin_writesAnAuthEntry_withTheTypedEmailAsTheLabel() {
        service.recordLogin(ActivityAction.LOGIN_FAILED, null, "  typed@news.local\n", Map.of("reason", "invalid credentials"));

        ActivityLog entry = saved();
        assertThat(entry.getActor()).isNull();
        assertThat(entry.getTargetType()).isEqualTo(ActivityTargetType.AUTH);
        assertThat(entry.getTargetId()).isNull();
        assertThat(entry.getTargetLabel()).isEqualTo("typed@news.local");
        assertThat(entry.getIpAddress()).isEqualTo("203.0.113.9");
    }

    @Test
    void recordLogin_forASuccess_hasTheUserAsActorAndTarget() {
        service.recordLogin(ActivityAction.LOGIN_SUCCESS, editor, editor.getEmail(), null);

        ActivityLog entry = saved();
        assertThat(entry.getActor().getId()).isEqualTo(7L);
        assertThat(entry.getTargetId()).isEqualTo(7L);
        assertThat(entry.getActorRoles()).isEqualTo("EDITOR");
    }

    @Test
    void deleteExpired_usesTheRetentionOfTheConfiguration() {
        property.getActivityLog().setRetentionDays(30);
        when(activityLogRepository.deleteOlderThan(any())).thenReturn(4);

        int deleted = service.deleteExpired();

        assertThat(deleted).isEqualTo(4);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(activityLogRepository).deleteOlderThan(cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now().minusDays(30).plusMinutes(1))
                .isAfter(LocalDateTime.now().minusDays(30).minusMinutes(1));
    }
}
