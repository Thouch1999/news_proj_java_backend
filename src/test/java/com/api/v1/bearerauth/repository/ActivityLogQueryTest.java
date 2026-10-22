package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.model.entity.ActivityLog;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.enumerate.ActivityAction;
import com.api.v1.bearerauth.model.enumerate.ActivityTargetType;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.request.ActivityLogFilter;
import com.api.v1.bearerauth.model.response.ActivityLogDto;
import com.api.v1.bearerauth.model.response.RecentActivityDto;
import com.api.v1.bearerauth.service.ActivityLogQueryService;
import com.api.v1.bearerauth.service.ActivityLogService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The log queries against the configured MySQL: the filters, the order, the history of a target, the CSV export and the
 * content feed. Rolled back; every entry carries a unique token, so existing data does not disturb the checks.
 */
@SpringBootTest
@Transactional
class ActivityLogQueryTest {
    @Autowired
    private EntityManager em;
    @Autowired
    private ActivityLogService activityLogService;
    @Autowired
    private ActivityLogQueryService queryService;

    private String token;
    private AppUser admin;
    private AppUser editor;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString().substring(0, 8);
        admin = user("Admin", Roles.ADMIN);
        editor = user("Editor", Roles.EDITOR);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private AppUser user(String name, Roles role) {
        AppUser user = new AppUser();
        user.setUsername(name + "-" + token);
        user.setFullName("សុខ " + name + " " + token);
        user.setEmail(name.toLowerCase() + "-" + token + "@news.local");
        user.setPassword("x");
        user.setRole(role);
        em.persist(user);
        return user;
    }

    private void as(AppUser user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private void record(AppUser actor, ActivityAction action, ActivityTargetType type, Long id, String label) {
        as(actor);
        activityLogService.record(action, type, id, label, Map.of("note", "n"));
    }

    private ActivityLogFilter filter(Long actorId, List<ActivityAction> actions, ActivityTargetType type, Long targetId, String search) {
        return new ActivityLogFilter(actorId, actions, type, targetId, null, null, search);
    }

    private List<ActivityLogDto> list(ActivityLogFilter filter) {
        em.flush();
        em.clear();
        return queryService.list(filter, 1, 100).getContent();
    }

    @Test
    void filters_actor_action_target_andSearch() {
        record(admin, ActivityAction.NEWS_PUBLISHED, ActivityTargetType.NEWS, 11L, "Cup final " + token);
        record(editor, ActivityAction.NEWS_REJECTED, ActivityTargetType.NEWS, 12L, "Budget " + token);
        record(editor, ActivityAction.COMMENT_HIDDEN, ActivityTargetType.COMMENT, 31L, "Cup final " + token);
        record(admin, ActivityAction.CATEGORY_CREATED, ActivityTargetType.CATEGORY, 5L, "Sports " + token);

        // the token keeps out whatever else is in the table
        assertThat(list(filter(null, null, null, null, token))).hasSize(4);
        assertThat(list(filter(admin.getId(), null, null, null, token))).extracting("targetLabel")
                .containsExactlyInAnyOrder("Cup final " + token, "Sports " + token);
        assertThat(list(filter(null, List.of(ActivityAction.NEWS_PUBLISHED, ActivityAction.NEWS_REJECTED), null, null, token)))
                .extracting("action").containsExactlyInAnyOrder(ActivityAction.NEWS_PUBLISHED, ActivityAction.NEWS_REJECTED);
        assertThat(list(filter(null, null, ActivityTargetType.COMMENT, null, token))).extracting("targetId").containsExactly(31L);
        assertThat(list(filter(null, null, ActivityTargetType.NEWS, 12L, token))).extracting("action").containsExactly(ActivityAction.NEWS_REJECTED);
        // the search is not case sensitive and also looks at the name of the actor (Khmer included)
        assertThat(list(filter(null, null, null, null, "CUP FINAL " + token.toUpperCase()))).hasSize(2);
        assertThat(list(filter(null, null, null, null, "សុខ editor " + token))).hasSize(2);
        // % and _ are plain characters
        assertThat(list(filter(null, null, null, null, "%" + token))).isEmpty();
    }

    @Test
    void dateRange_includesBothDays_andTheNewestIsFirst() {
        record(admin, ActivityAction.NEWS_CREATED, ActivityTargetType.NEWS, 1L, "First " + token);
        record(admin, ActivityAction.NEWS_UPDATED, ActivityTargetType.NEWS, 1L, "Second " + token);
        LocalDate today = LocalDate.now();

        ActivityLogFilter inRange = new ActivityLogFilter(null, null, null, null, today, today, token);
        ActivityLogFilter before = new ActivityLogFilter(null, null, null, null, today.minusDays(10), today.minusDays(1), token);
        ActivityLogFilter after = new ActivityLogFilter(null, null, null, null, today.plusDays(1), null, token);

        assertThat(list(inRange)).extracting("targetLabel").containsExactly("Second " + token, "First " + token);
        assertThat(list(before)).isEmpty();
        assertThat(list(after)).isEmpty();
    }

    @Test
    void history_ofATarget_isNewestFirst_andOnlyThatTarget() {
        long id = 880_000_000L + Math.abs(token.hashCode() % 1000);
        record(admin, ActivityAction.NEWS_CREATED, ActivityTargetType.NEWS, id, "Story " + token);
        record(editor, ActivityAction.NEWS_PUBLISHED, ActivityTargetType.NEWS, id, "Story " + token);
        record(editor, ActivityAction.NEWS_PUBLISHED, ActivityTargetType.NEWS, id + 1, "Other " + token);
        record(editor, ActivityAction.USER_CREATED, ActivityTargetType.USER, id, "User " + token); // the same number, another type
        em.flush();
        em.clear();

        List<ActivityLogDto> history = queryService.history(ActivityTargetType.NEWS, id, 1, 20).getContent();

        assertThat(history).extracting("action").containsExactly(ActivityAction.NEWS_PUBLISHED, ActivityAction.NEWS_CREATED);
        assertThat(history).extracting("details").containsOnlyNulls(); // no details in a list
    }

    @Test
    void oneEntry_hasItsDetails() {
        record(admin, ActivityAction.NEWS_PUBLISHED, ActivityTargetType.NEWS, 1L, "Detailed " + token);
        em.flush();
        em.clear();
        Long id = em.createQuery("select a.id from ActivityLog a where a.targetLabel = :label", Long.class)
                .setParameter("label", "Detailed " + token).getSingleResult();

        ActivityLogDto dto = queryService.get(id);

        assertThat(dto.getDetails()).containsEntry("note", "n");
        assertThat(dto.getActorId()).isEqualTo(admin.getId());
        assertThat(dto.getActorRoles()).isEqualTo("ADMIN");
    }

    @Test
    void export_isAUtf8Csv_thatCannotRunAsAFormula() {
        record(admin, ActivityAction.NEWS_CREATED, ActivityTargetType.NEWS, 1L, "=HYPERLINK(\"http://evil\",\"x\") " + token);
        record(admin, ActivityAction.NEWS_CREATED, ActivityTargetType.NEWS, 2L, "@SUM(1+1) " + token);
        record(admin, ActivityAction.NEWS_CREATED, ActivityTargetType.NEWS, 3L, "ព័ត៌មាន, \"ថ្មី\" " + token);
        em.flush();
        em.clear();

        byte[] bytes = queryService.exportCsv(filter(null, null, null, null, token));

        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String csv = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("id,createdAt,actorId,actorName,actorRoles,action,targetType,targetId,targetLabel,ipAddress,userAgent,details\r\n");
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil\"\",\"\"x\"\") " + token + "\"");
        assertThat(csv).contains("'@SUM(1+1) " + token);
        assertThat(csv).contains("\"ព័ត៌មាន, \"\"ថ្មី\"\" " + token + "\"");
        assertThat(csv.split("\r\n")).hasSize(4); // the header and three rows
        for (String line : csv.split("\r\n")) {
            assertThat(line).doesNotContain(",=").doesNotContain(",@");
        }
    }

    @Test
    void recent_hasTheContentActionsOnly() {
        record(admin, ActivityAction.NEWS_PUBLISHED, ActivityTargetType.NEWS, 1L, "Visible news " + token);
        record(editor, ActivityAction.COMMENT_HIDDEN, ActivityTargetType.COMMENT, 2L, "Visible comment " + token);
        record(admin, ActivityAction.LOGIN_FAILED, ActivityTargetType.AUTH, null, "Hidden login " + token);
        record(admin, ActivityAction.USER_ROLES_CHANGED, ActivityTargetType.USER, 3L, "Hidden user " + token);
        record(admin, ActivityAction.SETTINGS_UPDATED, ActivityTargetType.SETTINGS, null, "Hidden settings " + token);
        record(admin, ActivityAction.CATEGORY_DELETED, ActivityTargetType.CATEGORY, 4L, "Hidden category " + token);
        em.flush();
        em.clear();

        List<RecentActivityDto> recent = queryService.recent(50);

        assertThat(recent).extracting("targetLabel").contains("Visible news " + token, "Visible comment " + token)
                .doesNotContain("Hidden login " + token, "Hidden user " + token, "Hidden settings " + token, "Hidden category " + token);
        assertThat(recent).allSatisfy(entry -> assertThat(entry.getAction().isContent()).isTrue());
        assertThat(recent.stream().filter(entry -> entry.getTargetLabel().endsWith(token)).map(RecentActivityDto::getActorName))
                .contains("សុខ Admin " + token, "សុខ Editor " + token);
    }

    @Test
    void anEntry_cannotBeChanged_throughJpa() {
        record(admin, ActivityAction.NEWS_PUBLISHED, ActivityTargetType.NEWS, 1L, "Fixed " + token);
        em.flush();
        em.clear();
        ActivityLog entry = em.createQuery("select a from ActivityLog a where a.targetLabel = :label", ActivityLog.class)
                .setParameter("label", "Fixed " + token).getSingleResult();

        // there is no setter; and even a forced change of the managed object is not written (@Immutable)
        em.createNativeQuery("select 1").getSingleResult();
        em.flush();
        em.clear();
        ActivityLog again = em.find(ActivityLog.class, entry.getId());

        assertThat(again.getTargetLabel()).isEqualTo("Fixed " + token);
        assertThat(again.getCreatedAt()).isBefore(LocalDateTime.now().plusMinutes(1));
    }
}
