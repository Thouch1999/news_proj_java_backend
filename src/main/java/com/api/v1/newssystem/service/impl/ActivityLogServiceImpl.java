package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.model.entity.ActivityLog;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.ActivityTargetType;
import com.api.v1.newssystem.repository.ActivityLogRepository;
import com.api.v1.newssystem.repository.AppUserRepository;
import com.api.v1.newssystem.security.ClientIpResolver;
import com.api.v1.newssystem.security.CurrentUserService;
import com.api.v1.newssystem.service.ActivityLogService;
import com.api.v1.newssystem.util.ActivityDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogServiceImpl implements ActivityLogService {
    private static final int NAME_MAX = 150;
    private static final int LABEL_MAX = 255;
    private static final int USER_AGENT_MAX = 200;

    private final ActivityLogRepository activityLogRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;
    private final AppProperty appProperty;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY) // the same transaction as the action
    public void record(ActivityAction action, ActivityTargetType targetType, Long targetId, String targetLabel, Map<String, Object> details) {
        recordFor(currentUserService.findCurrentUser().orElse(null), action, targetType, targetId, targetLabel, details);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFor(AppUser actor, ActivityAction action, ActivityTargetType targetType, Long targetId, String targetLabel, Map<String, Object> details) {
        activityLogRepository.save(entry(actor, action, targetType, targetId, targetLabel, details));
    }

    @Override
    public void recordLogin(ActivityAction action, AppUser actor, String label, Map<String, Object> details) {
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> activityLogRepository.save(
                    entry(actor, action, ActivityTargetType.AUTH, actor == null ? null : actor.getId(), label, details)));
        } catch (Exception e) {
            log.error("Activity log: {} not written", action, e); // a login must not fail because the log cannot be written
        }
    }

    @Override
    @Transactional
    public int deleteExpired() {
        return activityLogRepository.deleteOlderThan(LocalDateTime.now().minusDays(appProperty.getActivityLog().getRetentionDays()));
    }

    // ---- helpers ----

    private ActivityLog entry(AppUser actor, ActivityAction action, ActivityTargetType targetType, Long targetId,
                              String targetLabel, Map<String, Object> details) {
        HttpServletRequest request = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
        return ActivityLog.builder()
                .actor(actor == null ? null : appUserRepository.getReferenceById(actor.getId()))
                .actorName(actor == null ? null : ActivityDetails.oneLine(displayName(actor), NAME_MAX))
                .actorRoles(actor == null || actor.getRole() == null ? null : actor.getRole().name())
                .ipAddress(clientIpResolver.resolve(request))
                .userAgent(request == null ? null : ActivityDetails.oneLine(request.getHeader("User-Agent"), USER_AGENT_MAX))
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .targetLabel(ActivityDetails.oneLine(targetLabel, LABEL_MAX))
                .details(toJson(ActivityDetails.sanitize(details)))
                .createdAt(LocalDateTime.now())
                .build();
    }

    // the name the person goes by: the full name, else the username, else the email
    private String displayName(AppUser user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return user.getEmail();
    }

    private String toJson(Map<String, Object> details) {
        if (details == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(details);
            return json.length() > ActivityDetails.JSON_MAX ? "{\"truncated\":true}" : json;
        } catch (JsonProcessingException e) {
            log.warn("Activity log: details not written as JSON", e);
            return null;
        }
    }
}
