package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.ActivityLog;
import com.api.v1.bearerauth.model.enumerate.ActivityAction;
import com.api.v1.bearerauth.model.enumerate.ActivityTargetType;
import com.api.v1.bearerauth.model.request.ActivityLogFilter;
import com.api.v1.bearerauth.model.response.ActivityLogDto;
import com.api.v1.bearerauth.model.response.RecentActivityDto;
import com.api.v1.bearerauth.repository.ActivityLogRepository;
import com.api.v1.bearerauth.service.ActivityLogQueryService;
import com.api.v1.bearerauth.util.CsvUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogQueryServiceImpl implements ActivityLogQueryService {
    private static final int MAX_PAGE_SIZE = 100;
    // the actions whose sentence in the list needs a short value of the details (the role, the roles, on or off, the method)
    private static final Set<ActivityAction> LIST_WITH_DETAILS = EnumSet.of(ActivityAction.USER_CREATED,
            ActivityAction.USER_ROLES_CHANGED, ActivityAction.COMMENTS_TOGGLED, ActivityAction.PASSWORD_CHANGED,
            ActivityAction.SUPER_ADMIN_TRANSFERRED);
    private static final int MAX_RECENT = 50;
    private static final int MAX_SEARCH_LENGTH = 100;
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}; // so a spreadsheet reads Khmer correctly
    private static final List<String> CSV_HEADER = List.of("id", "createdAt", "actorId", "actorName", "actorRoles", "action",
            "targetType", "targetId", "targetLabel", "ipAddress", "userAgent", "details");

    private final ActivityLogRepository activityLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<ActivityLogDto> list(ActivityLogFilter filter, int page, int size) {
        return find(filter, PageRequest.of(Math.max(page, 1) - 1, clamp(size, MAX_PAGE_SIZE), NEWEST))
                .map(entry -> toDto(entry, LIST_WITH_DETAILS.contains(entry.getAction())));
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityLogDto get(Long id) {
        ActivityLog entry = activityLogRepository.findById(id)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.ACTIVITY_LOG_NOT_FOUND.getDefaultMessage()));
        return toDto(entry, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ActivityLogDto> history(ActivityTargetType targetType, Long targetId, int page, int size) {
        ActivityLogFilter filter = new ActivityLogFilter(null, null, targetType, targetId, null, null, null);
        return list(filter, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCsv(ActivityLogFilter filter) {
        List<ActivityLog> rows = find(filter, PageRequest.of(0, EXPORT_MAX_ROWS, NEWEST)).getContent();
        StringBuilder csv = new StringBuilder(CsvUtil.row(CSV_HEADER));
        for (ActivityLog entry : rows) {
            csv.append(CsvUtil.row(Arrays.asList(
                    entry.getId(),
                    entry.getCreatedAt(),
                    entry.getActor() == null ? null : entry.getActor().getId(),
                    entry.getActorName(),
                    entry.getActorRoles(),
                    entry.getAction(),
                    entry.getTargetType(),
                    entry.getTargetId(),
                    entry.getTargetLabel(),
                    entry.getIpAddress(),
                    entry.getUserAgent(),
                    entry.getDetails())));
        }
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, withBom, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, withBom, UTF8_BOM.length, body.length);
        return withBom;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecentActivityDto> recent(int limit) {
        return activityLogRepository.findAllByActionIn(ActivityAction.CONTENT_ACTIONS, PageRequest.of(0, clamp(limit, MAX_RECENT), NEWEST)).stream()
                .map(entry -> new RecentActivityDto(entry.getId(), entry.getAction(), entry.getTargetType(), entry.getTargetId(),
                        entry.getTargetLabel(), entry.getActorName(), entry.getCreatedAt()))
                .toList();
    }

    // ---- helpers ----

    private Page<ActivityLog> find(ActivityLogFilter filter, Pageable pageable) {
        ActivityLogFilter f = filter == null ? ActivityLogFilter.none() : filter;
        if (f.from() != null && f.to() != null && f.from().isAfter(f.to())) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.INVALID_PARAMETER.getDefaultMessage());
        }
        boolean allActions = f.actions() == null || f.actions().isEmpty();
        return activityLogRepository.search(
                f.actorId(),
                allActions,
                allActions ? List.of(ActivityAction.LOGIN_SUCCESS) : f.actions(), // ignored when allActions
                f.targetType(),
                f.targetId(),
                f.from() == null ? null : f.from().atStartOfDay(),
                f.to() == null ? null : f.to().plusDays(1).atStartOfDay(), // `to` is included: up to the start of the next day
                likePattern(f.search()),
                pageable);
    }

    private ActivityLogDto toDto(ActivityLog entry, boolean withDetails) {
        return new ActivityLogDto(
                entry.getId(),
                entry.getActor() == null ? null : entry.getActor().getId(),
                entry.getActorName(),
                entry.getActorRoles(),
                entry.getIpAddress(),
                entry.getUserAgent(),
                entry.getAction(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getTargetLabel(),
                withDetails ? parse(entry.getDetails()) : null,
                entry.getCreatedAt());
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException e) {
            log.warn("Activity log: details are not valid JSON", e);
            return null;
        }
    }

    private int clamp(int value, int max) {
        return Math.min(Math.max(value, 1), max);
    }

    // the LIKE pattern of a search text: lower case, % _ and the escape character itself are matched literally
    private String likePattern(String search) {
        String trimmed = search == null || search.isBlank() ? null : search.trim();
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > MAX_SEARCH_LENGTH) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.INVALID_PARAMETER.getDefaultMessage());
        }
        String escaped = trimmed.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
