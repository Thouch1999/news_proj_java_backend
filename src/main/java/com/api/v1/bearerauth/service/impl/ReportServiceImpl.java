package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.enumerate.CommentStatus;
import com.api.v1.bearerauth.model.enumerate.NewsStatus;
import com.api.v1.bearerauth.model.enumerate.ReportInterval;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.response.ActivityItemDto;
import com.api.v1.bearerauth.model.response.AuthorDashboardDto;
import com.api.v1.bearerauth.model.response.CategoryNewsCountDto;
import com.api.v1.bearerauth.model.response.DashboardSummaryDto;
import com.api.v1.bearerauth.model.response.PeriodStatsDto;
import com.api.v1.bearerauth.model.response.PublishedOverTimeDto;
import com.api.v1.bearerauth.model.response.TimeSeriesPointDto;
import com.api.v1.bearerauth.model.response.TopNewsDto;
import com.api.v1.bearerauth.model.response.TrendsDto;
import com.api.v1.bearerauth.repository.ReportRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.service.ReportService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_DAYS = 30;
    private static final int DEFAULT_MONTHS = 12;
    private static final long MAX_DAYS = 366;
    private static final long MAX_MONTHS = 60;

    private final ReportRepository reportRepository;
    private final CurrentUserService currentUserService;

    @Override
    @Transactional
    public DashboardSummaryDto summary() {
        requireStaff();
        Map<Roles, Long> users = zeroFilled(Roles.class, reportRepository.countUsersByRole());
        Map<NewsStatus, Long> news = zeroFilled(NewsStatus.class, reportRepository.countNewsByStatus());
        return new DashboardSummaryDto(
                sum(users),
                users,
                sum(news),
                news,
                reportRepository.sumViews(),
                reportRepository.countComments(),
                reportRepository.countCommentsByStatus(CommentStatus.HIDDEN),
                reportRepository.countUnverifiedUsers());
    }

    @Override
    @Transactional
    public List<CategoryNewsCountDto> newsPerCategory() {
        requireStaff();
        return reportRepository.countNewsPerCategory();
    }

    @Override
    @Transactional
    public List<TopNewsDto> mostViewed(int limit, LocalDate from, LocalDate to) {
        Long authorId = scopeAuthorId();
        checkOrder(from, to);
        PageRequest top = PageRequest.of(0, clamp(limit));
        if (from == null && to == null) {
            return reportRepository.findMostViewedAllTime(authorId, top);
        }
        return reportRepository.findMostViewedBetween(start(from), endExclusive(to), authorId, top);
    }

    @Override
    @Transactional
    public List<TopNewsDto> mostCommented(int limit, LocalDate from, LocalDate to) {
        Long authorId = scopeAuthorId();
        checkOrder(from, to);
        return reportRepository.findMostCommented(start(from), endExclusive(to), authorId, PageRequest.of(0, clamp(limit)));
    }

    @Override
    @Transactional
    public PublishedOverTimeDto publishedOverTime(ReportInterval interval, LocalDate from, LocalDate to) {
        Long authorId = scopeAuthorId();
        return overTime(interval, from, to, authorId, reportRepository::countPublishedPerDay, reportRepository::countPublishedPerMonth);
    }

    @Override
    @Transactional
    public PublishedOverTimeDto viewsOverTime(ReportInterval interval, LocalDate from, LocalDate to) {
        Long authorId = scopeAuthorId();
        return overTime(interval, from, to, authorId, reportRepository::countViewsPerDay, reportRepository::countViewsPerMonth);
    }

    // the series of both reports: the same range rules, only the rows counted differ
    private PublishedOverTimeDto overTime(ReportInterval interval, LocalDate from, LocalDate to, Long authorId, Source perDay, Source perMonth) {
        ReportInterval kind = interval == null ? ReportInterval.DAY : interval;
        LocalDate last = to == null ? LocalDate.now() : to;
        LocalDate first = from != null ? from
                : kind == ReportInterval.DAY ? last.minusDays(DEFAULT_DAYS - 1) : last.minusMonths(DEFAULT_MONTHS - 1).withDayOfMonth(1);
        checkOrder(first, last);

        List<TimeSeriesPointDto> points = kind == ReportInterval.DAY ? perDay(first, last, authorId, perDay) : perMonth(first, last, authorId, perMonth);
        long total = points.stream().mapToLong(TimeSeriesPointDto::getCount).sum();
        return new PublishedOverTimeDto(kind, first, last, total, points);
    }

    @Override
    @Transactional
    public AuthorDashboardDto authorDashboard() {
        AppUser user = currentUserService.getCurrentUser();
        if (user.getRole() != Roles.AUTHOR) {
            throw forbidden();
        }
        Map<NewsStatus, Long> news = zeroFilled(NewsStatus.class, reportRepository.countNewsByStatusOfAuthor(user.getId()));
        return new AuthorDashboardDto(sum(news), news, reportRepository.sumViewsOfPublishedByAuthor(user.getId()),
                reportRepository.countCommentsOnArticlesOfAuthor(user.getId()));
    }

    @Override
    @Transactional
    public TrendsDto trends(LocalDate from, LocalDate to) {
        Long authorId = scopeAuthorId();
        LocalDate last = to == null ? LocalDate.now() : to;
        LocalDate first = from != null ? from : last.minusDays(DEFAULT_DAYS - 1);
        checkOrder(first, last);
        long days = ChronoUnit.DAYS.between(first, last) + 1;
        if (days > MAX_DAYS) {
            throw tooLong();
        }
        LocalDate previousLast = first.minusDays(1);
        LocalDate previousFirst = previousLast.minusDays(days - 1);
        return new TrendsDto(first, last, previousFirst, previousLast,
                periodStats(first, last, authorId), periodStats(previousFirst, previousLast, authorId));
    }

    private PeriodStatsDto periodStats(LocalDate first, LocalDate last, Long authorId) {
        LocalDateTime start = first.atStartOfDay();
        LocalDateTime end = last.plusDays(1).atStartOfDay();
        return new PeriodStatsDto(
                authorId == null ? reportRepository.countUsersCreatedBetween(start, end) : 0,
                reportRepository.countPublishedBetween(start, end, authorId),
                reportRepository.countViewsBetween(start, end, authorId),
                reportRepository.countCommentsBetween(start, end, authorId));
    }

    @Override
    @Transactional
    public List<ActivityItemDto> recentActivity(int limit) {
        Long authorId = scopeAuthorId();
        PageRequest top = PageRequest.of(0, clamp(limit));
        List<ActivityItemDto> items = new ArrayList<>(reportRepository.findRecentStatusChanges(authorId, top));
        items.addAll(reportRepository.findRecentComments(authorId, top));
        items.sort(Comparator.comparing(ActivityItemDto::getAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return items.size() > top.getPageSize() ? new ArrayList<>(items.subList(0, top.getPageSize())) : items;
    }

    // ---- published over time ----

    // one point for every day of the range, 0 for a day with nothing published
    private List<TimeSeriesPointDto> perDay(LocalDate first, LocalDate last, Long authorId, Source source) {
        if (first.plusDays(MAX_DAYS - 1).isBefore(last)) { // more than MAX_DAYS days, both ends included
            throw tooLong();
        }
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Object[] row : source.rows(first.atStartOfDay(), last.plusDays(1).atStartOfDay(), clockOffset(), authorId)) {
            counts.put(LocalDate.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue(), ((Number) row[2]).intValue()), ((Number) row[3]).longValue());
        }
        List<TimeSeriesPointDto> points = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            points.add(new TimeSeriesPointDto(day.toString(), counts.getOrDefault(day, 0L)));
        }
        return points;
    }

    // one point for every month of the range; the first and last month count only the days inside the range
    private List<TimeSeriesPointDto> perMonth(LocalDate first, LocalDate last, Long authorId, Source source) {
        YearMonth from = YearMonth.from(first);
        YearMonth to = YearMonth.from(last);
        if (from.plusMonths(MAX_MONTHS - 1).isBefore(to)) { // more than MAX_MONTHS months, both ends included
            throw tooLong();
        }
        Map<YearMonth, Long> counts = new HashMap<>();
        for (Object[] row : source.rows(first.atStartOfDay(), last.plusDays(1).atStartOfDay(), clockOffset(), authorId)) {
            counts.put(YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()), ((Number) row[2]).longValue());
        }
        List<TimeSeriesPointDto> points = new ArrayList<>();
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            points.add(new TimeSeriesPointDto(month.toString(), counts.getOrDefault(month, 0L)));
        }
        return points;
    }

    // ---- helpers ----

    // The JVM time zone's offset from UTC as "+07:00" (see ReportRepository: dates are stored in UTC, the application
    // runs on this clock, and a report day must be a day on this clock).
    String clockOffset() {
        int seconds = ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds();
        int absolute = Math.abs(seconds);
        return String.format("%s%02d:%02d", seconds < 0 ? "-" : "+", absolute / 3600, absolute % 3600 / 60);
    }

    // a map with every constant of the enum: the counts from the query, 0 for the constants it did not return
    private <E extends Enum<E>> Map<E, Long> zeroFilled(Class<E> type, List<Object[]> rows) {
        Map<E, Long> map = new EnumMap<>(type);
        for (E constant : type.getEnumConstants()) {
            map.put(constant, 0L);
        }
        for (Object[] row : rows) {
            map.put(type.cast(row[0]), ((Number) row[1]).longValue());
        }
        return map;
    }

    private long sum(Map<?, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    private int clamp(int limit) {
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private LocalDateTime start(LocalDate from) {
        return from == null ? null : from.atStartOfDay();
    }

    // `to` is a day that is included: the range ends at the start of the next day
    private LocalDateTime endExclusive(LocalDate to) {
        return to == null ? null : to.plusDays(1).atStartOfDay();
    }

    private void checkOrder(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.INVALID_PARAMETER.getDefaultMessage());
        }
    }

    private CustomException tooLong() {
        return new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.REPORT_RANGE_TOO_LONG.getDefaultMessage());
    }

    private void requireStaff() {
        Roles role = currentUserService.getCurrentUser().getRole();
        if (role != Roles.ADMIN && role != Roles.EDITOR) {
            throw forbidden();
        }
    }

    // null for EDITOR and ADMIN (the whole site); the id of an AUTHOR (only their own articles); anybody else is refused
    private Long scopeAuthorId() {
        AppUser user = currentUserService.getCurrentUser();
        if (user.getRole() == Roles.AUTHOR) {
            return user.getId();
        }
        if (user.getRole() != Roles.ADMIN && user.getRole() != Roles.EDITOR) {
            throw forbidden();
        }
        return null;
    }

    // the rows of one query of ReportRepository for a range
    @FunctionalInterface
    private interface Source {
        List<Object[]> rows(LocalDateTime from, LocalDateTime to, String offset, Long authorId);
    }

    private CustomException forbidden() {
        return new CustomException(HttpStatus.FORBIDDEN, MessageConstant.INSUFFICIENT_PERMISSIONS.getDefaultMessage());
    }
}
