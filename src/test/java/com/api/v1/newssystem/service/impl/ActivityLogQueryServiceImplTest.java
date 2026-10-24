package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.model.entity.ActivityLog;
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.ActivityTargetType;
import com.api.v1.newssystem.model.request.ActivityLogFilter;
import com.api.v1.newssystem.model.response.ActivityLogDto;
import com.api.v1.newssystem.model.response.RecentActivityDto;
import com.api.v1.newssystem.repository.ActivityLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityLogQueryServiceImplTest {
    @Mock
    private ActivityLogRepository activityLogRepository;

    private ActivityLogQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ActivityLogQueryServiceImpl(activityLogRepository, new ObjectMapper());
    }

    private ActivityLog entry(long id, String label, String details) {
        return ActivityLog.builder().id(id).actorName("Admin").actorRoles("ADMIN").ipAddress("203.0.113.9").userAgent("UA")
                .action(ActivityAction.NEWS_PUBLISHED).targetType(ActivityTargetType.NEWS).targetId(5L).targetLabel(label)
                .details(details).createdAt(LocalDateTime.of(2026, 3, 1, 9, 30)).build();
    }

    @SuppressWarnings("unchecked")
    private Page<ActivityLog> found(ActivityLog... entries) {
        Page<ActivityLog> page = new PageImpl<>(List.of(entries));
        when(activityLogRepository.search(any(), anyBoolean(), any(Collection.class), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        return page;
    }

    @Test
    void list_turnsTheFilterIntoTheQueryParameters() {
        found();
        ActivityLogFilter filter = new ActivityLogFilter(4L, List.of(ActivityAction.NEWS_PUBLISHED, ActivityAction.NEWS_REJECTED),
                ActivityTargetType.NEWS, 9L, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 10), "  50%_Off! ");

        service.list(filter, 2, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(activityLogRepository).search(eq(4L), eq(false), eq(filter.actions()), eq(ActivityTargetType.NEWS), eq(9L),
                eq(LocalDateTime.of(2026, 1, 5, 0, 0)), eq(LocalDateTime.of(2026, 1, 11, 0, 0)), // `to` is a whole day, included
                eq("%50!%!_off!!%"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100); // clamped
        assertThat(pageable.getValue().getSort().toString()).isEqualTo("createdAt: DESC,id: DESC");
    }

    @Test
    void list_withoutFilters_meansAnyAction() {
        found();

        service.list(null, 1, 20);

        verify(activityLogRepository).search(isNull(), eq(true), any(Collection.class), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void list_fromAfterTo_isABadRequest() {
        ActivityLogFilter filter = new ActivityLogFilter(null, null, null, null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> service.list(filter, 1, 20))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void list_hasNoDetails_butGetHas() {
        Page<ActivityLog> page = found(entry(1, "Cup final", "{\"from\":\"PENDING\"}"));
        when(activityLogRepository.findById(1L)).thenReturn(java.util.Optional.of(page.getContent().get(0)));

        ActivityLogDto inList = service.list(ActivityLogFilter.none(), 1, 20).getContent().get(0);
        ActivityLogDto one = service.get(1L);

        assertThat(inList.getDetails()).isNull();
        assertThat(one.getDetails()).containsEntry("from", "PENDING");
        assertThat(one.getIpAddress()).isEqualTo("203.0.113.9");
        assertThat(one.getActorName()).isEqualTo("Admin");
    }

    @Test
    void list_hasTheDetailsOnlyForTheActionsWhoseSentenceNeedsThem() {
        ActivityLog created = ActivityLog.builder().id(2L).actorName("Admin").action(ActivityAction.USER_CREATED)
                .targetType(ActivityTargetType.USER).targetId(8L).targetLabel("newadmin").details("{\"role\":\"ADMIN\"}")
                .createdAt(LocalDateTime.of(2026, 3, 1, 9, 30)).build();
        ActivityLog roles = ActivityLog.builder().id(3L).actorName("Admin").action(ActivityAction.USER_ROLES_CHANGED)
                .targetType(ActivityTargetType.USER).targetId(8L).targetLabel("newadmin")
                .details("{\"roles\":{\"before\":[\"READER\"],\"after\":[\"AUTHOR\"]}}").createdAt(LocalDateTime.of(2026, 3, 1, 9, 31)).build();
        found(created, roles, entry(1, "Cup final", "{\"from\":\"PENDING\"}"));

        List<ActivityLogDto> items = service.list(ActivityLogFilter.none(), 1, 20).getContent();

        assertThat(items.get(0).getDetails()).containsEntry("role", "ADMIN");
        assertThat(items.get(1).getDetails()).containsKey("roles");
        assertThat(items.get(2).getDetails()).isNull(); // NEWS_PUBLISHED: only in the entry itself
    }

    @Test
    void get_unknownEntry_is404() {
        when(activityLogRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(e.getMessage()).isEqualTo(MessageConstant.ACTIVITY_LOG_NOT_FOUND.getDefaultMessage());
        });
    }

    @Test
    void history_filtersOnTheTargetOnly() {
        found();

        service.history(ActivityTargetType.USER, 3L, 1, 20);

        verify(activityLogRepository).search(isNull(), eq(true), any(Collection.class), eq(ActivityTargetType.USER), eq(3L),
                isNull(), isNull(), isNull(), any(Pageable.class));
    }

    // ---------------- CSV export ----------------

    private String csv(byte[] bytes) {
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF); // the BOM, so a spreadsheet reads Khmer
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }

    @Test
    void export_takesAtMost10000Rows_newestFirst() {
        found();

        service.exportCsv(ActivityLogFilter.none());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(activityLogRepository).search(any(), anyBoolean(), any(Collection.class), any(), any(), any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10_000).isEqualTo(com.api.v1.newssystem.service.ActivityLogQueryService.EXPORT_MAX_ROWS);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    @Test
    void export_hasAHeaderAndOneLinePerEntry_withKhmerText() {
        found(entry(1, "ព័ត៌មានថ្មី", "{\"from\":\"PENDING\",\"to\":\"PUBLISHED\"}"));

        String text = csv(service.exportCsv(ActivityLogFilter.none()));

        String[] lines = text.split("\r\n");
        assertThat(lines[0]).isEqualTo("id,createdAt,actorId,actorName,actorRoles,action,targetType,targetId,targetLabel,ipAddress,userAgent,details");
        assertThat(lines).hasSize(2);
        assertThat(lines[1]).contains("ព័ត៌មានថ្មី").contains("NEWS_PUBLISHED").contains("\"{\"\"from\"\":\"\"PENDING\"\",\"\"to\"\":\"\"PUBLISHED\"\"}\"");
    }

    @Test
    void export_escapesValuesThatAFormulaCouldStartWith() {
        ActivityLog evil = ActivityLog.builder().id(2L).actorName("=cmd|' /C calc'!A0").actorRoles("ADMIN").action(ActivityAction.NEWS_CREATED)
                .targetType(ActivityTargetType.NEWS).targetLabel("@SUM(1+1)").userAgent("+1-1").details("-5")
                .createdAt(LocalDateTime.of(2026, 3, 1, 9, 30)).build();
        found(evil);

        String text = csv(service.exportCsv(ActivityLogFilter.none()));

        String line = text.split("\r\n")[1];
        assertThat(line).contains("'=cmd|' /C calc'!A0").contains("'@SUM(1+1)").contains("'+1-1").contains("'-5");
        for (String cell : line.split(",")) {
            assertThat(cell).doesNotStartWith("=").doesNotStartWith("@").doesNotStartWith("+").doesNotStartWith("-");
        }
    }

    // ---------------- recent ----------------

    @Test
    void recent_asksForTheContentActionsOnly_andHasNoAddressOrDetails() {
        when(activityLogRepository.findAllByActionIn(any(), any(Pageable.class))).thenReturn(List.of(entry(1, "Cup final", "{\"a\":1}")));

        List<RecentActivityDto> recent = service.recent(500);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ActivityAction>> actions = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(activityLogRepository).findAllByActionIn(actions.capture(), pageable.capture());
        assertThat(actions.getValue()).isNotEmpty().allMatch(ActivityAction::isContent)
                .doesNotContain(ActivityAction.LOGIN_SUCCESS, ActivityAction.LOGIN_FAILED, ActivityAction.PASSWORD_CHANGED,
                        ActivityAction.USER_CREATED, ActivityAction.USER_ROLES_CHANGED, ActivityAction.SETTINGS_UPDATED,
                        ActivityAction.CATEGORY_CREATED, ActivityAction.TAG_DELETED)
                .contains(ActivityAction.NEWS_PUBLISHED, ActivityAction.COMMENT_HIDDEN, ActivityAction.COMMENTS_TOGGLED);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).getTargetLabel()).isEqualTo("Cup final");
        assertThat(recent.get(0).getActorName()).isEqualTo("Admin");
        assertThat(RecentActivityDto.class.getDeclaredFields()).extracting("name")
                .doesNotContain("ipAddress", "userAgent", "details", "actorRoles");
    }
}
