package com.api.v1.newssystem.config;

import com.api.v1.newssystem.repository.AppUserRepository;
import com.api.v1.newssystem.repository.CategoryRepository;
import com.api.v1.newssystem.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;

/**
 * DEVELOPMENT only: inserts 20 demo news articles (db/seed/dev/seed_news_20.sql) after {@link DataSeeder}.
 * It runs only when the "dev" profile is active and app.seed.demo-news is true (application-dev.yaml), so never in
 * production and not in the tests. It is not a migration. It runs once: when the first demo article exists it does
 * nothing. A missing user or category, or an error in the script, is logged as a warning and the application starts anyway.
 */
@Component
@Profile("dev")
@Order(2) // after DataSeeder (roles, users, categories, tags)
@RequiredArgsConstructor
@Slf4j
public class DemoNewsSeeder implements ApplicationRunner {

    private static final String SCRIPT = "db/seed/dev/seed_news_20.sql";
    private static final String FIRST_DEMO_SLUG = "cambodian-startups-ai-chatbots-khmer-customer-service";
    private static final int DEMO_COUNT = 20;
    private static final List<String> CATEGORY_SLUGS = List.of(
            "business", "education", "entertainment", "health", "society", "sports", "technology");

    private final AppProperty appProperty;
    private final AppUserRepository appUserRepository;
    private final CategoryRepository categoryRepository;
    private final NewsRepository newsRepository;
    private final DataSource dataSource;
    private final CacheManager cacheManager;

    @Override
    public void run(ApplicationArguments args) {
        AppProperty.Seed seed = appProperty.getSeed();
        if (!seed.isDemoNews()) {
            return;
        }
        if (newsRepository.existsBySlug(FIRST_DEMO_SLUG)) {
            log.info("Demo news already present, skipping");
            return;
        }

        Long author = userId(seed.getAuthor());
        Long admin = userId(seed.getAdmin());
        Long editor = userId(seed.getEditor());
        if (author == null || admin == null || editor == null) {
            log.warn("Demo news skipped: the seeded Author, Admin or Editor user was not found");
            return;
        }
        List<String> missing = CATEGORY_SLUGS.stream().filter(slug -> !categoryRepository.existsBySlug(slug)).toList();
        if (!missing.isEmpty()) {
            log.warn("Demo news skipped: these categories were not found: {}", missing);
            return;
        }

        try {
            // one transaction on one connection: the script uses session variables and is all or nothing
            new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> insert(author, admin, editor));
            log.info("Inserted {} demo news articles", DEMO_COUNT);
            // nothing about news or reports is cached at the moment; this keeps it right if a cache is added
            cacheManager.getCacheNames().forEach(name -> {
                var cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            });
        } catch (RuntimeException exception) {
            log.warn("Demo news not inserted (rolled back): {}", exception.getMessage(), exception);
        }
    }

    private Long userId(AppProperty.SeedUser user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return null;
        }
        return appUserRepository.findByEmail(user.getEmail()).map(found -> found.getId()).orElse(null);
    }

    private void insert(Long author, Long admin, Long editor) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource); // joins the transaction of the template: same connection
        jdbc.update("SET @author = ?", author);
        jdbc.update("SET @admin = ?", admin);
        jdbc.update("SET @editor = ?", editor);

        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource(SCRIPT), StandardCharsets.UTF_8));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }

        Integer inserted = jdbc.queryForObject("SELECT COUNT(*) FROM news WHERE thumbnail LIKE 'https://picsum.photos/seed/ponleu%'", Integer.class);
        if (inserted == null || inserted != DEMO_COUNT) {
            throw new IllegalStateException("Expected " + DEMO_COUNT + " demo articles but found " + inserted);
        }
    }
}
