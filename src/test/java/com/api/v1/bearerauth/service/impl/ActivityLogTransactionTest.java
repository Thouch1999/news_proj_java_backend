package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.ActivityLog;
import com.api.v1.bearerauth.model.enumerate.ActivityAction;
import com.api.v1.bearerauth.model.enumerate.ActivityTargetType;
import com.api.v1.bearerauth.model.request.AuthRequest;
import com.api.v1.bearerauth.repository.ActivityLogRepository;
import com.api.v1.bearerauth.service.ActivityLogService;
import com.api.v1.bearerauth.service.AuthService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The transaction rules of the log, against the configured MySQL with REAL commits (so this class is not @Transactional and
 * removes its own rows): an entry lives and dies with the transaction of its action, and a login entry is written on its own.
 * The retention is raised for this class so the cleanup test can never touch an entry that is not its own.
 */
@SpringBootTest(properties = "app.activity-log.retention-days=36500")
class ActivityLogTransactionTest {
    @Autowired
    private ActivityLogService activityLogService;
    @Autowired
    private ActivityLogRepository activityLogRepository;
    @Autowired
    private AuthService authService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager em;

    private String token;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        token = "tx-" + UUID.randomUUID().toString().substring(0, 8);
        tx = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void removeMyRows() {
        tx.executeWithoutResult(status -> em.createNativeQuery("delete from activity_logs where target_label like ?1")
                .setParameter(1, token + "%").executeUpdate());
    }

    private long mine() {
        return tx.execute(status -> em.createQuery("select count(a) from ActivityLog a where a.targetLabel like :label", Long.class)
                .setParameter("label", token + "%").getSingleResult());
    }

    @Test
    void anEntryCannotBeWrittenWithoutATransaction() {
        assertThatThrownBy(() -> activityLogService.record(ActivityAction.NEWS_CREATED, ActivityTargetType.NEWS, 1L, token, null))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(mine()).isZero();
    }

    @Test
    void anEntry_isKept_whenTheActionCommits() {
        tx.executeWithoutResult(status ->
                activityLogService.record(ActivityAction.NEWS_CREATED, ActivityTargetType.NEWS, 1L, token + " committed", Map.of("a", "b")));

        assertThat(mine()).isEqualTo(1);
    }

    @Test
    void anEntry_isGone_whenTheActionFailsAfterwards() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            activityLogService.record(ActivityAction.NEWS_PUBLISHED, ActivityTargetType.NEWS, 1L, token + " failed", null);
            throw new CustomException(org.springframework.http.HttpStatus.CONFLICT, "the action failed after it was logged");
        })).isInstanceOf(CustomException.class);

        assertThat(mine()).isZero(); // a logged action always really happened
    }

    @Test
    void anEntry_isGone_whenTheTransactionIsMarkedForRollback() {
        tx.executeWithoutResult(status -> {
            activityLogService.record(ActivityAction.NEWS_PUBLISHED, ActivityTargetType.NEWS, 1L, token + " rolled back", null);
            status.setRollbackOnly();
        });

        assertThat(mine()).isZero();
    }

    @Test
    void aFailedLogin_isWrittenOnItsOwn_withTheTypedEmail_andNeverThePassword() {
        String typed = token + "@news.local";
        AuthRequest request = new AuthRequest();
        request.setEmail(typed);
        request.setPassword("Typed-Secret-123");

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(CustomException.class); // no transaction around it

        List<ActivityLog> found = tx.execute(status -> em.createQuery("select a from ActivityLog a where a.targetLabel = :label", ActivityLog.class)
                .setParameter("label", typed).getResultList());
        assertThat(found).hasSize(1);
        ActivityLog entry = found.get(0);
        assertThat(entry.getAction()).isEqualTo(ActivityAction.LOGIN_FAILED);
        assertThat(entry.getTargetType()).isEqualTo(ActivityTargetType.AUTH);
        assertThat(entry.getActor()).isNull();
        assertThat(entry.getTargetId()).isNull();
        assertThat(entry.getDetails()).isEqualTo("{\"reason\":\"invalid credentials\"}");
        assertThat(String.join("|", entry.getTargetLabel(), entry.getDetails(), String.valueOf(entry.getUserAgent()))).doesNotContain("Typed-Secret-123");
    }

    @Test
    void theRetentionJob_deletesOldEntriesOnly() {
        LocalDateTime now = LocalDateTime.now();
        tx.executeWithoutResult(status -> {
            activityLogRepository.save(ActivityLog.builder().action(ActivityAction.NEWS_CREATED).targetType(ActivityTargetType.NEWS)
                    .targetLabel(token + " ancient").createdAt(now.minusDays(40_000)).build());
            activityLogRepository.save(ActivityLog.builder().action(ActivityAction.NEWS_CREATED).targetType(ActivityTargetType.NEWS)
                    .targetLabel(token + " recent").createdAt(now.minusDays(1)).build());
        });
        assertThat(mine()).isEqualTo(2);

        int deleted = activityLogService.deleteExpired();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        List<String> left = tx.execute(status -> em.createQuery("select a.targetLabel from ActivityLog a where a.targetLabel like :label", String.class)
                .setParameter("label", token + "%").getResultList());
        assertThat(left).containsExactly(token + " recent");
    }
}
