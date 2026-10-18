package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.enumerate.CommentStatus;
import com.api.v1.bearerauth.model.enumerate.NewsStatus;
import com.api.v1.bearerauth.model.enumerate.ReportInterval;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.response.ActivityItemDto;
import com.api.v1.bearerauth.model.response.AuthorDashboardDto;
import com.api.v1.bearerauth.model.response.DashboardSummaryDto;
import com.api.v1.bearerauth.model.response.PublishedOverTimeDto;
import com.api.v1.bearerauth.model.response.TimeSeriesPointDto;
import com.api.v1.bearerauth.model.response.TrendsDto;
import com.api.v1.bearerauth.repository.ReportRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private CurrentUserService currentUserService;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(reportRepository, currentUserService);
        loginAs(Roles.EDITOR, 5L);
    }

    private void loginAs(Roles role, long id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    private void assertFails(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(status));
    }

    private Object[] row(Object... values) {
        return values;
    }

    // ---------------- summary ----------------

    @Test
    void summary_hasEveryRoleAndEveryStatus_withZeroWhereThereIsNone_andTheTotals() {
        when(reportRepository.countUsersByRole()).thenReturn(List.of(row(Roles.ADMIN, 1L), row(Roles.READER, 40L)));
        when(reportRepository.countNewsByStatus()).thenReturn(List.of(row(NewsStatus.DRAFT, 3L), row(NewsStatus.PUBLISHED, 7L)));
        when(reportRepository.sumViews()).thenReturn(1234L);
        when(reportRepository.countComments()).thenReturn(50L);
        when(reportRepository.countCommentsByStatus(CommentStatus.HIDDEN)).thenReturn(4L);

        DashboardSummaryDto summary = service.summary();

        assertThat(summary.getTotalUsers()).isEqualTo(41);
        assertThat(summary.getUsersByRole()).containsOnlyKeys(Roles.values());
        assertThat(summary.getUsersByRole().get(Roles.READER)).isEqualTo(40);
        assertThat(summary.getUsersByRole().get(Roles.EDITOR)).isZero();
        assertThat(summary.getTotalNews()).isEqualTo(10);
        assertThat(summary.getNewsByStatus()).containsOnlyKeys(NewsStatus.values());
        assertThat(summary.getNewsByStatus().get(NewsStatus.PUBLISHED)).isEqualTo(7);
        assertThat(summary.getNewsByStatus().get(NewsStatus.PENDING)).isZero();
        assertThat(summary.getNewsByStatus().get(NewsStatus.REJECTED)).isZero();
        assertThat(summary.getTotalViews()).isEqualTo(1234);
        assertThat(summary.getTotalComments()).isEqualTo(50);
        assertThat(summary.getHiddenComments()).isEqualTo(4);
    }

    @Test
    void summary_withNoData_isAllZeros() {
        DashboardSummaryDto summary = service.summary();

        assertThat(summary.getTotalUsers()).isZero();
        assertThat(summary.getTotalNews()).isZero();
        assertThat(summary.getUsersByRole().values()).containsOnly(0L);
        assertThat(summary.getNewsByStatus().values()).containsOnly(0L);
    }

    @Test
    void wholeSiteReports_areForEditorAndAdminOnly() {
        for (Roles role : List.of(Roles.AUTHOR, Roles.READER)) {
            loginAs(role, 5L);

            assertFails(() -> service.summary(), HttpStatus.FORBIDDEN);
            assertFails(() -> service.newsPerCategory(), HttpStatus.FORBIDDEN);
        }
        verifyNoInteractions(reportRepository);

        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            loginAs(role, 5L);
            service.summary();
            service.newsPerCategory();
        }
    }

    // ---------------- per category ----------------

    @Test
    void newsPerCategory_isWhatTheAggregateQueryReturns() {
        service.newsPerCategory();

        verify(reportRepository).countNewsPerCategory();
    }

    // ---------------- top lists ----------------

    @Test
    void mostViewed_withoutDates_ranksByTheAllTimeCounter() {
        service.mostViewed(5, null, null);

        ArgumentCaptor<Pageable> top = ArgumentCaptor.forClass(Pageable.class);
        verify(reportRepository).findMostViewedAllTime(isNull(), top.capture());
        assertThat(top.getValue().getPageSize()).isEqualTo(5);
        assertThat(top.getValue().getPageNumber()).isZero();
        verify(reportRepository, never()).findMostViewedBetween(any(), any(), any(), any());
    }

    @Test
    void mostViewed_withDates_ranksByTheViewsInThePeriod_bothDaysIncluded() {
        service.mostViewed(5, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 10));

        verify(reportRepository).findMostViewedBetween(eq(LocalDateTime.of(2026, 1, 5, 0, 0)), eq(LocalDateTime.of(2026, 1, 11, 0, 0)), isNull(), any(Pageable.class));
        verify(reportRepository, never()).findMostViewedAllTime(any(), any());
    }

    @Test
    void mostViewed_withOnlyOneDate_leavesTheOtherEndOpen() {
        service.mostViewed(5, LocalDate.of(2026, 1, 5), null);
        service.mostViewed(5, null, LocalDate.of(2026, 1, 10));

        verify(reportRepository).findMostViewedBetween(eq(LocalDateTime.of(2026, 1, 5, 0, 0)), isNull(), isNull(), any(Pageable.class));
        verify(reportRepository).findMostViewedBetween(isNull(), eq(LocalDateTime.of(2026, 1, 11, 0, 0)), isNull(), any(Pageable.class));
    }

    @Test
    void mostCommented_passesThePeriod_orNullsForAllTime() {
        service.mostCommented(7, null, null);
        service.mostCommented(7, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        verify(reportRepository).findMostCommented(isNull(), isNull(), isNull(), any(Pageable.class));
        verify(reportRepository).findMostCommented(eq(LocalDateTime.of(2026, 2, 1, 0, 0)), eq(LocalDateTime.of(2026, 3, 1, 0, 0)), isNull(), any(Pageable.class));
    }

    @Test
    void topLists_keepTheLimitBetween1And50() {
        service.mostViewed(0, null, null);
        service.mostViewed(9999, null, null);
        service.mostCommented(-3, null, null);

        ArgumentCaptor<Pageable> viewed = ArgumentCaptor.forClass(Pageable.class);
        verify(reportRepository, org.mockito.Mockito.times(2)).findMostViewedAllTime(isNull(), viewed.capture());
        assertThat(viewed.getAllValues()).extracting(Pageable::getPageSize).containsExactly(1, 50);
        ArgumentCaptor<Pageable> commented = ArgumentCaptor.forClass(Pageable.class);
        verify(reportRepository).findMostCommented(any(), any(), isNull(), commented.capture());
        assertThat(commented.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void topLists_withFromAfterTo_areABadRequest() {
        assertFails(() -> service.mostViewed(5, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)), HttpStatus.BAD_REQUEST);
        assertFails(() -> service.mostCommented(5, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)), HttpStatus.BAD_REQUEST);

        verifyNoInteractions(reportRepository);
    }

    @Test
    void listsSeriesTrendsAndActivity_areRefusedToAReader() {
        loginAs(Roles.READER, 5L);

        assertFails(() -> service.mostViewed(10, null, null), HttpStatus.FORBIDDEN);
        assertFails(() -> service.mostCommented(10, null, null), HttpStatus.FORBIDDEN);
        assertFails(() -> service.publishedOverTime(ReportInterval.DAY, null, null), HttpStatus.FORBIDDEN);
        assertFails(() -> service.viewsOverTime(ReportInterval.DAY, null, null), HttpStatus.FORBIDDEN);
        assertFails(() -> service.trends(null, null), HttpStatus.FORBIDDEN);
        assertFails(() -> service.recentActivity(8), HttpStatus.FORBIDDEN);
        verifyNoInteractions(reportRepository);
    }

    @Test
    void summary_hasTheUnverifiedUsers() {
        when(reportRepository.countUnverifiedUsers()).thenReturn(6L);

        assertThat(service.summary().getUnverifiedUsers()).isEqualTo(6);
    }

    // ---------------- top lists, series, trends, activity: whole site or own articles ----------------

    @Test
    void topLists_coverTheWholeSiteForStaff_andOnlyTheOwnArticlesForAnAuthor() {
        service.mostViewed(10, null, null);
        verify(reportRepository).findMostViewedAllTime(isNull(), any());

        loginAs(Roles.AUTHOR, 42L);
        service.mostViewed(10, null, null);
        service.mostCommented(10, LocalDate.of(2026, 3, 1), null);
        verify(reportRepository).findMostViewedAllTime(eq(42L), any());
        verify(reportRepository).findMostCommented(any(), isNull(), eq(42L), any());
    }

    @Test
    void viewsOverTime_hasAPointForEveryDay_andIsScopedLikeThePublishedSeries() {
        loginAs(Roles.AUTHOR, 42L);
        when(reportRepository.countViewsPerDay(any(), any(), anyString(), any())).thenReturn(java.util.Collections.singletonList(row(2026, 3, 2, 30L)));

        PublishedOverTimeDto report = service.viewsOverTime(ReportInterval.DAY, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3));

        assertThat(report.getPoints()).extracting(TimeSeriesPointDto::getCount).containsExactly(0L, 30L, 0L);
        assertThat(report.getTotal()).isEqualTo(30);
    }

    @Test
    void viewsOverTime_perMonth_hasAPointForEveryMonth() {
        when(reportRepository.countViewsPerMonth(any(), any(), anyString(), isNull())).thenReturn(java.util.Collections.singletonList(row(2026, 2, 9L)));

        PublishedOverTimeDto report = service.viewsOverTime(ReportInterval.MONTH, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 3, 5));

        assertThat(report.getPoints()).extracting(TimeSeriesPointDto::getPeriod).containsExactly("2026-01", "2026-02", "2026-03");
        assertThat(report.getPoints()).extracting(TimeSeriesPointDto::getCount).containsExactly(0L, 9L, 0L);
    }

    @Test
    void trends_comparesWithThePeriodOfTheSameLengthRightBefore() {
        when(reportRepository.countUsersCreatedBetween(LocalDateTime.of(2026, 3, 11, 0, 0), LocalDateTime.of(2026, 3, 21, 0, 0))).thenReturn(4L);
        when(reportRepository.countUsersCreatedBetween(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 3, 11, 0, 0))).thenReturn(2L);
        when(reportRepository.countViewsBetween(any(), any(), isNull())).thenReturn(10L);

        TrendsDto trends = service.trends(LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 20));

        assertThat(trends.getPreviousFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(trends.getPreviousTo()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(trends.getCurrent().getNewUsers()).isEqualTo(4);
        assertThat(trends.getPrevious().getNewUsers()).isEqualTo(2);
        assertThat(trends.getCurrent().getViews()).isEqualTo(10);
    }

    @Test
    void trends_forAnAuthor_countOnlyTheirArticles_andNoUsers() {
        loginAs(Roles.AUTHOR, 42L);
        when(reportRepository.countViewsBetween(any(), any(), eq(42L))).thenReturn(7L);

        TrendsDto trends = service.trends(LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 20));

        assertThat(trends.getCurrent().getViews()).isEqualTo(7);
        assertThat(trends.getCurrent().getNewUsers()).isZero();
        verify(reportRepository, never()).countUsersCreatedBetween(any(), any());
    }

    @Test
    void trends_defaultToTheLast30Days_andRefuseABadRange() {
        TrendsDto trends = service.trends(null, null);

        assertThat(trends.getTo()).isEqualTo(LocalDate.now());
        assertThat(trends.getFrom()).isEqualTo(LocalDate.now().minusDays(29));
        assertThat(trends.getPreviousTo()).isEqualTo(LocalDate.now().minusDays(30));
        assertFails(() -> service.trends(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 1)), HttpStatus.BAD_REQUEST);
        assertFails(() -> service.trends(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 3, 1)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void recentActivity_mergesStatusChangesAndComments_newestFirst_cutToTheLimit() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 10, 12, 0);
        ActivityItemDto approved = new ActivityItemDto("Ann", "ann", 1L, "A", "a", NewsStatus.PENDING, NewsStatus.PUBLISHED, now.minusMinutes(5));
        ActivityItemDto created = new ActivityItemDto(null, "bob", 2L, "B", "b", null, NewsStatus.DRAFT, now.minusHours(3));
        ActivityItemDto comment = new ActivityItemDto("Cy", "cy", 1L, "A", "a", "x".repeat(200), now.minusMinutes(1));
        when(reportRepository.findRecentStatusChanges(isNull(), any())).thenReturn(List.of(approved, created));
        when(reportRepository.findRecentComments(isNull(), any())).thenReturn(List.of(comment));

        List<ActivityItemDto> items = service.recentActivity(2);

        assertThat(items).containsExactly(comment, approved);
        assertThat(comment.getType()).isEqualTo(ActivityItemDto.COMMENT);
        assertThat(comment.getSnippet()).hasSize(121); // 120 characters and the ellipsis
        assertThat(created.getActorName()).isEqualTo("bob"); // no full name: the username
        assertThat(approved.getType()).isEqualTo(ActivityItemDto.STATUS_CHANGE);
    }

    @Test
    void recentActivity_forAnAuthor_isScopedToTheirArticles() {
        loginAs(Roles.AUTHOR, 42L);

        service.recentActivity(8);

        verify(reportRepository).findRecentStatusChanges(eq(42L), any());
        verify(reportRepository).findRecentComments(eq(42L), any());
    }

    // ---------------- published over time ----------------

    @Test
    void perDay_hasAPointForEveryDay_zeroWhereNothingWasPublished() {
        when(reportRepository.countPublishedPerDay(any(), any(), anyString(), any())).thenReturn(List.of(row(2026, 3, 2, 3L), row(2026, 3, 4, 1L)));

        PublishedOverTimeDto report = service.publishedOverTime(ReportInterval.DAY, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5));

        assertThat(report.getInterval()).isEqualTo(ReportInterval.DAY);
        assertThat(report.getPoints()).extracting(TimeSeriesPointDto::getPeriod)
                .containsExactly("2026-03-01", "2026-03-02", "2026-03-03", "2026-03-04", "2026-03-05");
        assertThat(report.getPoints()).extracting(TimeSeriesPointDto::getCount).containsExactly(0L, 3L, 0L, 1L, 0L);
        assertThat(report.getTotal()).isEqualTo(4);
        verify(reportRepository).countPublishedPerDay(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 3, 6, 0, 0), service.clockOffset(), null);
    }

    @Test
    void perMonth_hasAPointForEveryMonth_acrossAYearEnd() {
        when(reportRepository.countPublishedPerMonth(any(), any(), anyString(), any())).thenReturn(List.of(row(2025, 12, 4L), row(2026, 2, 6L)));

        PublishedOverTimeDto report = service.publishedOverTime(ReportInterval.MONTH, LocalDate.of(2025, 11, 15), LocalDate.of(2026, 2, 3));

        assertThat(report.getPoints()).extracting(TimeSeriesPointDto::getPeriod).containsExactly("2025-11", "2025-12", "2026-01", "2026-02");
        assertThat(report.getPoints()).extracting(TimeSeriesPointDto::getCount).containsExactly(0L, 4L, 0L, 6L);
        assertThat(report.getTotal()).isEqualTo(10);
        verify(reportRepository).countPublishedPerMonth(LocalDateTime.of(2025, 11, 15, 0, 0), LocalDateTime.of(2026, 2, 4, 0, 0), service.clockOffset(), null);
    }

    @Test
    void clockOffset_isTheJvmZoneOffsetFromUtc_asPlusOrMinusHoursAndMinutes() {
        assertThat(service.clockOffset()).matches("[+-]\\d{2}:\\d{2}");

        java.util.TimeZone saved = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Phnom_Penh"));
            assertThat(service.clockOffset()).isEqualTo("+07:00");
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
            assertThat(service.clockOffset()).isEqualTo("+00:00");
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/St_Johns"));
            assertThat(service.clockOffset()).matches("-0[23]:\\d{2}"); // -03:30 or -02:30 in summer
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
            assertThat(service.clockOffset()).isEqualTo("+05:30");
        } finally {
            java.util.TimeZone.setDefault(saved);
        }
    }

    @Test
    void published_defaults_areTheLast30Days_orTheLast12Months_endingToday() {
        PublishedOverTimeDto days = service.publishedOverTime(null, null, null);
        PublishedOverTimeDto months = service.publishedOverTime(ReportInterval.MONTH, null, null);

        assertThat(days.getInterval()).isEqualTo(ReportInterval.DAY);
        assertThat(days.getPoints()).hasSize(30);
        assertThat(days.getTo()).isEqualTo(LocalDate.now());
        assertThat(days.getFrom()).isEqualTo(LocalDate.now().minusDays(29));
        assertThat(months.getPoints()).hasSize(12);
        assertThat(months.getFrom().getDayOfMonth()).isEqualTo(1);
    }

    @Test
    void published_toDefaultsToToday_whenOnlyFromIsGiven() {
        PublishedOverTimeDto report = service.publishedOverTime(ReportInterval.DAY, LocalDate.now().minusDays(2), null);

        assertThat(report.getPoints()).hasSize(3);
        assertThat(report.getTo()).isEqualTo(LocalDate.now());
    }

    @Test
    void published_aSingleDay_worksAndFromAfterToIsABadRequest() {
        LocalDate day = LocalDate.of(2026, 3, 1);

        assertThat(service.publishedOverTime(ReportInterval.DAY, day, day).getPoints()).hasSize(1);
        assertFails(() -> service.publishedOverTime(ReportInterval.DAY, day.plusDays(1), day), HttpStatus.BAD_REQUEST);
    }

    @Test
    void published_rangeLimits_366Days_and60Months() {
        LocalDate from = LocalDate.of(2025, 1, 1);

        assertThat(service.publishedOverTime(ReportInterval.DAY, from, from.plusDays(365)).getPoints()).hasSize(366);
        assertFails(() -> service.publishedOverTime(ReportInterval.DAY, from, from.plusDays(366)), HttpStatus.BAD_REQUEST);
        assertThat(service.publishedOverTime(ReportInterval.MONTH, from, LocalDate.of(2029, 12, 31)).getPoints()).hasSize(60);
        assertFails(() -> service.publishedOverTime(ReportInterval.MONTH, from, LocalDate.of(2030, 1, 1)), HttpStatus.BAD_REQUEST);
    }

    // ---------------- author dashboard ----------------

    @Test
    void authorDashboard_countsOnlyTheLoggedInAuthorsArticles() {
        loginAs(Roles.AUTHOR, 42L);
        when(reportRepository.countNewsByStatusOfAuthor(42L)).thenReturn(List.of(row(NewsStatus.DRAFT, 2L), row(NewsStatus.PUBLISHED, 5L), row(NewsStatus.REJECTED, 1L)));
        when(reportRepository.sumViewsOfPublishedByAuthor(42L)).thenReturn(900L);
        when(reportRepository.countCommentsOnArticlesOfAuthor(42L)).thenReturn(12L);

        AuthorDashboardDto dashboard = service.authorDashboard();

        assertThat(dashboard.getTotalNews()).isEqualTo(8);
        assertThat(dashboard.getNewsByStatus()).containsOnlyKeys(NewsStatus.values());
        assertThat(dashboard.getNewsByStatus().get(NewsStatus.PUBLISHED)).isEqualTo(5);
        assertThat(dashboard.getNewsByStatus().get(NewsStatus.ARCHIVED)).isZero();
        assertThat(dashboard.getTotalViews()).isEqualTo(900);
        assertThat(dashboard.getTotalComments()).isEqualTo(12);
        verify(reportRepository, never()).countNewsByStatus(); // the whole-site numbers are not touched
    }

    @Test
    void authorDashboard_isForAuthorsOnly() {
        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN, Roles.READER)) {
            loginAs(role, 5L);

            assertFails(() -> service.authorDashboard(), HttpStatus.FORBIDDEN);
        }
        verifyNoInteractions(reportRepository);
    }
}
