package com.api.v1.newssystem.config;

import com.api.v1.newssystem.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * The one-time data step of "the square logo replaces the mobile bar image" (see "Sidebar slideshow" in CLAUDE.md). There is no
 * migration tool: Hibernate has already added the logo columns (ddl-auto: update) when this runs. It is a new step, the earlier
 * SponsorSchemaMigration is not edited. For every sponsor without a logo that has an UPLOADED bar image (a bar creative that
 * is not the generated crop) it makes the 256x256 and 128x128 variants with the image service, stores them as the logo and
 * deletes the old bar creative and its file. Safe to run again (it finds nothing the second time); an error is logged for that
 * sponsor and never thrown. The generated crops stay: they are the fallback of the bar.
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class SponsorLogoMigration implements ApplicationRunner {
    private static final int LOGO_SIZE = 256;
    private static final int LOGO_SMALL_SIZE = 128;

    private final JdbcTemplate jdbc;
    private final FileStorageService fileStorageService;

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    select c.id as creative_id, c.sponsor_id as sponsor_id, c.image_url as image_url
                    from sponsor_creatives c join sponsors s on s.id = c.sponsor_id
                    where c.placement = 'MOBILE_BOTTOM_BAR' and c.is_generated = 0 and s.logo_url is null
                    """);
        } catch (RuntimeException e) {
            log.warn("Sponsors: could not look for bar images to move into the logo", e);
            return;
        }
        int moved = 0;
        for (Map<String, Object> row : rows) {
            try {
                move(((Number) row.get("creative_id")).longValue(), ((Number) row.get("sponsor_id")).longValue(), (String) row.get("image_url"));
                moved++;
            } catch (RuntimeException e) {
                log.warn("Sponsors: could not move the bar image of sponsor {} into the logo", row.get("sponsor_id"), e);
            }
        }
        if (moved > 0) {
            log.info("Sponsors: moved {} mobile bar images into the logo", moved);
        }
    }

    private void move(long creativeId, long sponsorId, String url) {
        String big = fileStorageService.storeSquareCrop(url, LOGO_SIZE);
        String small = fileStorageService.storeSquareCrop(url, LOGO_SMALL_SIZE);
        boolean keepOriginal = big == null || small == null; // a picture the image service cannot read (WebP): used as it is
        if (keepOriginal) {
            fileStorageService.delete(big);
            fileStorageService.delete(small);
            big = url;
            small = url;
        }
        jdbc.update("update sponsors set logo_url = ?, logo_small_url = ? where id = ?", big, small, sponsorId);
        jdbc.update("delete from sponsor_creatives where id = ?", creativeId);
        if (!keepOriginal) {
            fileStorageService.delete(url);
        }
    }
}
