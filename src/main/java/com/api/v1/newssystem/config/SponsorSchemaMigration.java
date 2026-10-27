package com.api.v1.newssystem.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The one-time data steps of "the admin chooses the placements" (see "Sponsors" in CLAUDE.md). There is no migration tool:
 * Hibernate has already created the new table and columns (ddl-auto: update) when this runs, and these steps do what it
 * cannot. Every step is safe to run again (it finds nothing to do the second time) and an error is logged, never thrown.
 * <ol>
 *   <li>drops the old unique key of sponsor_daily_stats (sponsor, placement, day): the new one also has the device;</li>
 *   <li>moves the old statistics of MOBILE_BOTTOM_BAR to ARTICLE_POPUP with the device MOBILE;</li>
 *   <li>gives a sponsor that has no chosen placement yet (an existing one) the placements where an image exists.</li>
 * </ol>
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class SponsorSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        step("drop the old unique key of the statistics", this::dropOldStatsKey);
        step("move the mobile bar statistics under the popup", () -> jdbc.update(
                "update sponsor_daily_stats set placement = 'ARTICLE_POPUP', device = 'MOBILE' where placement = 'MOBILE_BOTTOM_BAR'"));
        step("choose the placements of the existing sponsors", () -> jdbc.update("""
                insert into sponsor_placements (sponsor_id, placement)
                select c.sponsor_id, c.placement from sponsor_creatives c
                where c.placement in ('ARTICLE_SIDEBAR', 'ARTICLE_POPUP')
                  and not exists (select 1 from sponsor_placements p where p.sponsor_id = c.sponsor_id)
                """));
    }

    private int dropOldStatsKey() {
        Integer found = jdbc.queryForObject("""
                select count(*) from information_schema.statistics
                where table_schema = database() and table_name = 'sponsor_daily_stats' and index_name = 'uk_sponsor_daily_stats'
                """, Integer.class);
        if (found != null && found > 0) {
            jdbc.execute("alter table sponsor_daily_stats drop index uk_sponsor_daily_stats");
            return 1;
        }
        return 0;
    }

    private void step(String what, java.util.function.IntSupplier action) {
        try {
            int changed = action.getAsInt();
            if (changed > 0) {
                log.info("Sponsors: {} ({} rows)", what, changed);
            }
        } catch (RuntimeException e) {
            log.warn("Sponsors: could not {}", what, e);
        }
    }
}
