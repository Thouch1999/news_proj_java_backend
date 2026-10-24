package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.model.entity.ActivityLog;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Category;
import com.api.v1.newssystem.model.entity.Comment;
import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.ActivityTargetType;
import com.api.v1.newssystem.model.enumerate.NewsStatus;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.request.AdminCreateUserRequest;
import com.api.v1.newssystem.model.request.AdminResetPasswordRequest;
import com.api.v1.newssystem.model.request.CategoryRequest;
import com.api.v1.newssystem.model.request.ChangePasswordRequest;
import com.api.v1.newssystem.model.request.ChangeRoleRequest;
import com.api.v1.newssystem.model.request.NewsRequest;
import com.api.v1.newssystem.model.request.SettingsUpdateRequest;
import com.api.v1.newssystem.model.request.TagRequest;
import com.api.v1.newssystem.model.response.CategoryDto;
import com.api.v1.newssystem.model.response.NewsDto;
import com.api.v1.newssystem.model.response.TagDto;
import com.api.v1.newssystem.service.ActivityLogQueryService;
import com.api.v1.newssystem.service.AppUserService;
import com.api.v1.newssystem.service.AuthService;
import com.api.v1.newssystem.service.CategoryService;
import com.api.v1.newssystem.service.CommentService;
import com.api.v1.newssystem.service.NewsService;
import com.api.v1.newssystem.service.SystemSettingService;
import com.api.v1.newssystem.service.TagService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every action type writes exactly one entry with the right snapshots, through the REAL services and the configured MySQL
 * (everything runs in one transaction that is rolled back, and every name carries a unique token, so nothing stays).
 * A failed action writes no entry; no secret reaches the log; an entry about a deleted article still has its title.
 */
@SpringBootTest
@Transactional
class ActivityLogRecordingTest {
    private static final String IP = "198.51.100.23";
    private static final String AGENT = "IntegrationTest/1.0";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private EntityManager em;
    @Autowired
    private NewsService newsService;
    @Autowired
    private CommentService commentService;
    @Autowired
    private AppUserService appUserService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private TagService tagService;
    @Autowired
    private SystemSettingService systemSettingService;
    @Autowired
    private AuthService authService;
    @Autowired
    private ActivityLogQueryService activityLogQueryService;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private String token;
    private AppUser admin;
    private AppUser editor;
    private AppUser author;
    private AppUser reader;
    private Category category;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString().substring(0, 8);
        admin = user("admin", Roles.ADMIN);
        editor = user("editor", Roles.EDITOR);
        author = user("author", Roles.AUTHOR);
        reader = user("reader", Roles.READER);
        category = new Category();
        category.setName("Activity " + token);
        category.setSlug("activity-" + token);
        em.persist(category);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(IP);
        request.addHeader("User-Agent", AGENT);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        loginAs(admin);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private AppUser user(String name, Roles role) {
        AppUser user = new AppUser();
        user.setUsername(name + "-" + token);
        user.setFullName(name + " Name " + token);
        user.setEmail(name + "-" + token + "@news.local");
        user.setPassword(passwordEncoder.encode("Old-Pass-1"));
        user.setRole(role);
        user.setActive(true);
        user.setEmailVerified(true);
        em.persist(user);
        return user;
    }

    private void loginAs(AppUser user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private List<ActivityLog> logs(ActivityAction action) {
        return em.createQuery("select a from ActivityLog a where a.action = :action and a.actor.id in :actors order by a.id", ActivityLog.class)
                .setParameter("action", action)
                .setParameter("actors", List.of(admin.getId(), editor.getId(), author.getId(), reader.getId()))
                .getResultList();
    }

    private long allMyLogs() {
        return em.createQuery("select count(a) from ActivityLog a where a.actor.id in :actors", Long.class)
                .setParameter("actors", List.of(admin.getId(), editor.getId(), author.getId(), reader.getId()))
                .getSingleResult();
    }

    private ActivityLog one(ActivityAction action) {
        List<ActivityLog> found = logs(action);
        assertThat(found).as(action.name()).hasSize(1);
        return found.get(0);
    }

    private JsonNode details(ActivityLog entry) throws Exception {
        assertThat(entry.getDetails()).isNotNull();
        return JSON.readTree(entry.getDetails());
    }

    private void assertSnapshot(ActivityLog entry, AppUser actor) {
        assertThat(entry.getActor().getId()).isEqualTo(actor.getId());
        assertThat(entry.getActorName()).isEqualTo(actor.getFullName());
        assertThat(entry.getActorRoles()).isEqualTo(actor.getRole().name());
        assertThat(entry.getIpAddress()).isEqualTo(IP);
        assertThat(entry.getUserAgent()).isEqualTo(AGENT);
        assertThat(entry.getCreatedAt()).isBetween(LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(1));
    }

    private NewsRequest newsRequest(String title, String content) {
        NewsRequest request = new NewsRequest();
        request.setTitle(title);
        request.setSummary("Summary " + token);
        request.setContent(content);
        request.setCategoryId(category.getId());
        return request;
    }

    // ---------------- articles ----------------

    @Test
    void theWholeLifeOfAnArticle_writesOneEntryPerAction() throws Exception {
        String title = "Cup final " + token;

        // the author writes it
        loginAs(author);
        NewsDto created = newsService.create(newsRequest(title, "<p>First text</p>"));
        ActivityLog createdLog = one(ActivityAction.NEWS_CREATED);
        assertSnapshot(createdLog, author);
        assertThat(createdLog.getTargetType()).isEqualTo(ActivityTargetType.NEWS);
        assertThat(createdLog.getTargetId()).isEqualTo(created.getId());
        assertThat(createdLog.getTargetLabel()).isEqualTo(title);
        assertThat(details(createdLog).get("category").asText()).isEqualTo(category.getName());

        // an edit: only the changed short fields, and for the content just a note
        newsService.update(created.getId(), newsRequest("Cup final " + token + " (new)", "<p>Second text, much longer</p>"));
        ActivityLog updatedLog = one(ActivityAction.NEWS_UPDATED);
        JsonNode changes = details(updatedLog).get("changes");
        assertThat(updatedLog.getTargetLabel()).isEqualTo("Cup final " + token + " (new)");
        assertThat(changes.get("title").get("before").asText()).isEqualTo(title);
        assertThat(changes.get("title").get("after").asText()).isEqualTo("Cup final " + token + " (new)");
        assertThat(changes.get("content").asText()).isEqualTo("content changed");
        assertThat(changes.has("summary")).isFalse();
        assertThat(updatedLog.getDetails()).doesNotContain("First text").doesNotContain("Second text");

        // an edit that changes nothing writes nothing
        newsService.update(created.getId(), newsRequest("Cup final " + token + " (new)", "<p>Second text, much longer</p>"));
        assertThat(logs(ActivityAction.NEWS_UPDATED)).hasSize(1);

        newsService.submit(created.getId());
        ActivityLog submitted = one(ActivityAction.NEWS_SUBMITTED);
        assertThat(details(submitted).get("from").asText()).isEqualTo("DRAFT");
        assertThat(details(submitted).get("to").asText()).isEqualTo("PENDING");

        // the editor rejects it, with a reason
        loginAs(editor);
        newsService.reject(created.getId(), "Add sources");
        ActivityLog rejected = one(ActivityAction.NEWS_REJECTED);
        assertSnapshot(rejected, editor);
        assertThat(details(rejected).get("reason").asText()).isEqualTo("Add sources");
        assertThat(details(rejected).get("from").asText()).isEqualTo("PENDING");
        assertThat(details(rejected).get("to").asText()).isEqualTo("REJECTED");

        // the author sends it again, the editor publishes it
        loginAs(author);
        newsService.submit(created.getId());
        assertThat(logs(ActivityAction.NEWS_SUBMITTED)).hasSize(2);
        loginAs(editor);
        newsService.publish(created.getId());
        ActivityLog published = one(ActivityAction.NEWS_PUBLISHED);
        assertSnapshot(published, editor);
        assertThat(published.getTargetLabel()).isEqualTo("Cup final " + token + " (new)");
        assertThat(details(published).get("to").asText()).isEqualTo("PUBLISHED");

        // the comments are closed, then the article is archived
        newsService.setCommentsEnabled(created.getId(), false);
        ActivityLog toggled = one(ActivityAction.COMMENTS_TOGGLED);
        assertThat(details(toggled).get("commentsEnabled").get("before").asBoolean()).isTrue();
        assertThat(details(toggled).get("commentsEnabled").get("after").asBoolean()).isFalse();
        newsService.setCommentsEnabled(created.getId(), false); // nothing changes: nothing is written
        assertThat(logs(ActivityAction.COMMENTS_TOGGLED)).hasSize(1);

        newsService.archive(created.getId());
        assertThat(details(one(ActivityAction.NEWS_ARCHIVED)).get("to").asText()).isEqualTo("ARCHIVED");

        // only the admin restores and deletes
        loginAs(admin);
        newsService.restore(created.getId());
        ActivityLog restored = one(ActivityAction.NEWS_RESTORED);
        assertSnapshot(restored, admin);
        assertThat(details(restored).get("from").asText()).isEqualTo("ARCHIVED");

        newsService.delete(created.getId());
        ActivityLog deleted = one(ActivityAction.NEWS_DELETED);
        assertSnapshot(deleted, admin);
        assertThat(deleted.getTargetId()).isEqualTo(created.getId());
        assertThat(deleted.getTargetLabel()).isEqualTo("Cup final " + token + " (new)");

        // the article is gone, its history still says which article it was
        em.flush();
        em.clear();
        assertThat(em.find(News.class, created.getId())).isNull();
        var history = activityLogQueryService.history(ActivityTargetType.NEWS, created.getId(), 1, 50).getContent();
        assertThat(history).extracting("action").contains(ActivityAction.NEWS_CREATED, ActivityAction.NEWS_PUBLISHED, ActivityAction.NEWS_DELETED);
        assertThat(history).allSatisfy(entry -> assertThat(entry.getTargetLabel()).startsWith("Cup final " + token));
        assertThat(history.get(0).getAction()).isEqualTo(ActivityAction.NEWS_DELETED); // newest first
    }

    @Test
    void aFailedAction_writesNoEntry() {
        loginAs(author);
        NewsDto draft = newsService.create(newsRequest("Draft " + token, "<p>x</p>"));
        long before = allMyLogs();

        // wrong role: an author cannot publish
        assertThatThrownBy(() -> newsService.publish(draft.getId())).isInstanceOf(CustomException.class);
        // wrong status: a draft cannot be archived
        loginAs(editor);
        assertThatThrownBy(() -> newsService.archive(draft.getId())).isInstanceOf(CustomException.class);
        // a rejection needs a reason
        assertThatThrownBy(() -> newsService.reject(draft.getId(), "  ")).isInstanceOf(CustomException.class);
        // an unknown article
        assertThatThrownBy(() -> newsService.delete(99_999_999L)).isInstanceOf(CustomException.class);
        // a category that does not exist
        loginAs(author);
        NewsRequest bad = newsRequest("Bad " + token, "<p>x</p>");
        bad.setCategoryId(99_999_999L);
        assertThatThrownBy(() -> newsService.create(bad)).isInstanceOf(CustomException.class);

        assertThat(allMyLogs()).isEqualTo(before);
        assertThat(logs(ActivityAction.NEWS_PUBLISHED)).isEmpty();
        assertThat(logs(ActivityAction.NEWS_ARCHIVED)).isEmpty();
        assertThat(logs(ActivityAction.NEWS_REJECTED)).isEmpty();
        assertThat(logs(ActivityAction.NEWS_DELETED)).isEmpty();
    }

    // ---------------- comments ----------------

    private Comment comment(News news, AppUser by) {
        Comment comment = new Comment();
        comment.setNews(news);
        comment.setUser(by);
        comment.setContent("A comment " + token);
        em.persist(comment);
        return comment;
    }

    private News publishedNews(String title) {
        News news = new News();
        news.setTitle(title);
        news.setSlug("news-" + token);
        news.setContent("<p>x</p>");
        news.setStatus(NewsStatus.PUBLISHED);
        news.setAuthor(author);
        news.setCategory(category);
        news.setPublishedAt(LocalDateTime.now());
        em.persist(news);
        return news;
    }

    @Test
    void comments_hideShowAndDelete_areLogged() throws Exception {
        News news = publishedNews("Commented " + token);
        Comment first = comment(news, reader);
        Comment second = comment(news, reader);

        loginAs(editor);
        commentService.hide(first.getId());
        ActivityLog hidden = one(ActivityAction.COMMENT_HIDDEN);
        assertSnapshot(hidden, editor);
        assertThat(hidden.getTargetType()).isEqualTo(ActivityTargetType.COMMENT);
        assertThat(hidden.getTargetId()).isEqualTo(first.getId());
        assertThat(hidden.getTargetLabel()).isEqualTo("Commented " + token);
        assertThat(details(hidden).get("newsId").asLong()).isEqualTo(news.getId());
        assertThat(details(hidden).get("commentAuthorId").asLong()).isEqualTo(reader.getId());
        assertThat(hidden.getDetails()).doesNotContain("A comment"); // the comment text is not copied

        commentService.hide(first.getId()); // already hidden: nothing changes, nothing is written
        assertThat(logs(ActivityAction.COMMENT_HIDDEN)).hasSize(1);

        commentService.unhide(first.getId());
        assertThat(one(ActivityAction.COMMENT_SHOWN).getTargetId()).isEqualTo(first.getId());

        commentService.delete(first.getId());
        ActivityLog byStaff = one(ActivityAction.COMMENT_DELETED);
        assertThat(details(byStaff).get("ownComment").asBoolean()).isFalse();

        loginAs(reader);
        commentService.delete(second.getId());
        List<ActivityLog> deleted = logs(ActivityAction.COMMENT_DELETED);
        assertThat(deleted).hasSize(2);
        assertThat(details(deleted.get(1)).get("ownComment").asBoolean()).isTrue();
        assertSnapshot(deleted.get(1), reader);
    }

    @Test
    void aCommentActionThatIsRefused_writesNoEntry() {
        News news = publishedNews("Refused " + token);
        Comment comment = comment(news, author);
        long before = allMyLogs();

        loginAs(reader);
        assertThatThrownBy(() -> commentService.hide(comment.getId())).isInstanceOf(CustomException.class); // not staff
        assertThatThrownBy(() -> commentService.delete(comment.getId())).isInstanceOf(CustomException.class); // not the author

        assertThat(allMyLogs()).isEqualTo(before);
    }

    // ---------------- users ----------------

    @Test
    void users_createActivateDeactivateRolesAndPasswordReset_areLogged_withoutAnySecret() throws Exception {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        request.setUsername("staff-" + token);
        request.setFullName("Staff " + token);
        request.setEmail("staff-" + token + "@news.local");
        request.setPassword("Staff-Secret-9");
        request.setConfirm("Staff-Secret-9");
        request.setRole(Roles.AUTHOR);
        Long id = appUserService.createStaff(request).getId();

        ActivityLog created = one(ActivityAction.USER_CREATED);
        assertSnapshot(created, admin);
        assertThat(created.getTargetType()).isEqualTo(ActivityTargetType.USER);
        assertThat(created.getTargetId()).isEqualTo(id);
        assertThat(created.getTargetLabel()).isEqualTo("staff-" + token);
        assertThat(details(created).get("role").asText()).isEqualTo("AUTHOR");

        appUserService.setActive(id, false);
        assertThat(one(ActivityAction.USER_DEACTIVATED).getTargetId()).isEqualTo(id);
        appUserService.setActive(id, false); // already inactive: nothing is written
        assertThat(logs(ActivityAction.USER_DEACTIVATED)).hasSize(1);
        appUserService.setActive(id, true);
        assertThat(one(ActivityAction.USER_ACTIVATED).getTargetId()).isEqualTo(id);

        ChangeRoleRequest role = new ChangeRoleRequest();
        role.setRole(Roles.EDITOR);
        appUserService.changeRole(id, role);
        ActivityLog changed = one(ActivityAction.USER_ROLES_CHANGED);
        assertThat(details(changed).get("roles").get("before").get(0).asText()).isEqualTo("AUTHOR");
        assertThat(details(changed).get("roles").get("after").get(0).asText()).isEqualTo("EDITOR");
        appUserService.changeRole(id, role); // the same role: nothing is written
        assertThat(logs(ActivityAction.USER_ROLES_CHANGED)).hasSize(1);

        AdminResetPasswordRequest reset = new AdminResetPasswordRequest();
        reset.setPassword("Reset-Secret-7");
        reset.setConfirm("Reset-Secret-7");
        appUserService.resetPassword(id, reset);
        ActivityLog resetLog = one(ActivityAction.USER_PASSWORD_RESET);
        assertThat(resetLog.getTargetId()).isEqualTo(id);
        assertThat(resetLog.getDetails()).isNull();

        for (ActivityAction action : List.of(ActivityAction.USER_CREATED, ActivityAction.USER_DEACTIVATED, ActivityAction.USER_ACTIVATED,
                ActivityAction.USER_ROLES_CHANGED, ActivityAction.USER_PASSWORD_RESET)) {
            assertNoSecret(one(action), "Staff-Secret-9", "Reset-Secret-7", "Old-Pass-1");
        }
    }

    @Test
    void theSuperAdminCreatesAUserOfEachRole_activeAndVerified_andTheLogHasTheRole() throws Exception {
        // a copy of the admin that is never saved, so the real super admin of the database is not touched
        AppUser boss = new AppUser();
        boss.setId(admin.getId());
        boss.setUsername(admin.getUsername());
        boss.setFullName(admin.getFullName());
        boss.setEmail(admin.getEmail());
        boss.setRole(Roles.ADMIN);
        boss.setSuperAdmin(true);
        loginAs(boss);
        for (Roles role : Roles.values()) {
            AdminCreateUserRequest request = new AdminCreateUserRequest();
            request.setUsername(role.name().toLowerCase() + "-new-" + token);
            request.setEmail(role.name().toLowerCase() + "-new-" + token + "@news.local");
            request.setPassword("Created-Secret-1");
            request.setConfirm("Created-Secret-1");
            request.setRole(role);

            var created = appUserService.createStaff(request);

            assertThat(created.getRole()).isEqualTo(role);
            assertThat(created.isActive()).as(role.name()).isTrue();
            assertThat(created.isEmailVerified()).as(role.name()).isTrue();
        }

        List<ActivityLog> entries = logs(ActivityAction.USER_CREATED);
        assertThat(entries).hasSize(Roles.values().length);
        for (int index = 0; index < entries.size(); index++) {
            Roles role = Roles.values()[index];
            assertSnapshot(entries.get(index), admin);
            assertThat(details(entries.get(index)).get("role").asText()).isEqualTo(role.name());
            assertThat(entries.get(index).getTargetLabel()).isEqualTo(role.name().toLowerCase() + "-new-" + token);
            assertNoSecret(entries.get(index), "Created-Secret-1");
        }
        // the list shows the role without opening the entry: an admin account cannot be created unseen
        var listed = activityLogQueryService.list(new com.api.v1.newssystem.model.request.ActivityLogFilter(admin.getId(),
                List.of(ActivityAction.USER_CREATED), null, null, null, null, null), 1, 20).getContent();
        assertThat(listed).hasSize(Roles.values().length);
        assertThat(listed).allSatisfy(entry -> assertThat(entry.getDetails()).containsKey("role"));
        assertThat(listed).extracting(entry -> entry.getDetails().get("role")).contains("ADMIN");
    }

    @Test
    void anAdminCannotDeactivateThemselves_andNothingIsWritten() {
        long before = allMyLogs();

        assertThatThrownBy(() -> appUserService.setActive(admin.getId(), false)).isInstanceOf(CustomException.class);

        assertThat(allMyLogs()).isEqualTo(before);
    }

    private void assertNoSecret(ActivityLog entry, String... secrets) {
        String everything = String.join("|", String.valueOf(entry.getDetails()), String.valueOf(entry.getTargetLabel()),
                String.valueOf(entry.getActorName()), String.valueOf(entry.getUserAgent()), String.valueOf(entry.getIpAddress()));
        for (String secret : secrets) {
            assertThat(everything).as(entry.getAction() + " must not hold " + secret).doesNotContain(secret);
        }
        assertThat(everything).doesNotContain("$2a$").doesNotContain("$2b$"); // no password hash either
    }

    // ---------------- categories and tags ----------------

    @Test
    void categories_createUpdateActivateAndDelete_areLogged() throws Exception {
        CategoryRequest request = new CategoryRequest();
        request.setName("Cat " + token);
        request.setDescription("About cats");
        CategoryDto created = categoryService.create(request);
        ActivityLog createdLog = one(ActivityAction.CATEGORY_CREATED);
        assertSnapshot(createdLog, admin);
        assertThat(createdLog.getTargetType()).isEqualTo(ActivityTargetType.CATEGORY);
        assertThat(createdLog.getTargetId()).isEqualTo(created.getId());
        assertThat(createdLog.getTargetLabel()).isEqualTo("Cat " + token);

        CategoryRequest same = new CategoryRequest();
        same.setName("Cat " + token);
        same.setDescription("About cats");
        categoryService.update(created.getId(), same); // nothing changed: nothing is written
        assertThat(logs(ActivityAction.CATEGORY_UPDATED)).isEmpty();

        CategoryRequest renamed = new CategoryRequest();
        renamed.setName("Cat " + token + " renamed");
        renamed.setDescription("About cats");
        categoryService.update(created.getId(), renamed);
        ActivityLog updated = one(ActivityAction.CATEGORY_UPDATED);
        assertThat(updated.getTargetLabel()).isEqualTo("Cat " + token + " renamed");
        assertThat(details(updated).get("changes").get("name").get("before").asText()).isEqualTo("Cat " + token);
        assertThat(details(updated).get("changes").get("name").get("after").asText()).isEqualTo("Cat " + token + " renamed");

        categoryService.setActive(created.getId(), false);
        List<ActivityLog> updates = logs(ActivityAction.CATEGORY_UPDATED);
        assertThat(updates).hasSize(2);
        assertThat(details(updates.get(1)).get("changes").get("active").get("after").asBoolean()).isFalse();

        categoryService.delete(created.getId());
        ActivityLog deleted = one(ActivityAction.CATEGORY_DELETED);
        assertThat(deleted.getTargetId()).isEqualTo(created.getId());
        assertThat(deleted.getTargetLabel()).isEqualTo("Cat " + token + " renamed"); // still named after it is gone
    }

    @Test
    void tags_createUpdateAndDelete_areLogged() throws Exception {
        TagRequest request = new TagRequest();
        request.setName("Tag " + token);
        TagDto created = tagService.create(request);
        ActivityLog createdLog = one(ActivityAction.TAG_CREATED);
        assertSnapshot(createdLog, admin);
        assertThat(createdLog.getTargetType()).isEqualTo(ActivityTargetType.TAG);
        assertThat(createdLog.getTargetLabel()).isEqualTo("Tag " + token);

        TagRequest renamed = new TagRequest();
        renamed.setName("Tag " + token + " renamed");
        tagService.update(created.getId(), renamed);
        ActivityLog updated = one(ActivityAction.TAG_UPDATED);
        assertThat(details(updated).get("changes").get("name").get("after").asText()).isEqualTo("Tag " + token + " renamed");

        tagService.delete(created.getId());
        ActivityLog deleted = one(ActivityAction.TAG_DELETED);
        assertThat(deleted.getTargetId()).isEqualTo(created.getId());
        assertThat(deleted.getTargetLabel()).isEqualTo("Tag " + token + " renamed");
    }

    // ---------------- settings ----------------

    @Test
    void settings_onlyTheChangedKeysAreLogged_andNeverTheValueOfANonPublicSetting() throws Exception {
        SettingsUpdateRequest request = new SettingsUpdateRequest();
        request.setValues(Map.of("site_name_en", "News " + token, "dashboard_name", "Dash " + token));
        systemSettingService.updateGroup("general", request);

        ActivityLog entry = one(ActivityAction.SETTINGS_UPDATED);
        assertSnapshot(entry, admin);
        assertThat(entry.getTargetType()).isEqualTo(ActivityTargetType.SETTINGS);
        assertThat(entry.getTargetId()).isNull();
        assertThat(entry.getTargetLabel()).isEqualTo("general");
        JsonNode changed = details(entry).get("changed");
        assertThat(details(entry).get("group").asText()).isEqualTo("general");
        assertThat(changed.get("site_name_en").get("after").asText()).isEqualTo("News " + token);
        assertThat(changed.get("dashboard_name").get("after").asText()).isEqualTo("Dash " + token);
        assertThat(changed.has("tagline_en")).isFalse(); // not sent, not changed

        // the same values again: nothing changed, nothing is written
        systemSettingService.updateGroup("general", request);
        assertThat(logs(ActivityAction.SETTINGS_UPDATED)).hasSize(1);

        // a setting that is not public: only the fact that it changed
        SettingsUpdateRequest content = new SettingsUpdateRequest();
        String flipped = systemSettingService.isCommentsEnabledDefault() ? "false" : "true";
        content.setValues(Map.of("comments_enabled_default", flipped));
        systemSettingService.updateGroup("content", content);
        List<ActivityLog> all = logs(ActivityAction.SETTINGS_UPDATED);
        assertThat(all).hasSize(2);
        JsonNode second = details(all.get(1)).get("changed");
        assertThat(second.get("comments_enabled_default").isTextual()).isTrue();
        assertThat(second.get("comments_enabled_default").asText()).isEqualTo("changed");
        assertThat(all.get(1).getDetails()).doesNotContain("\"" + flipped + "\"");
    }

    @Test
    void settings_anInvalidValue_writesNoEntry() {
        SettingsUpdateRequest request = new SettingsUpdateRequest();
        request.setValues(Map.of("site_name_en", "Fine " + token, "contact_email", "not-an-email"));
        long before = allMyLogs();

        assertThatThrownBy(() -> systemSettingService.updateGroup("general", request)).isInstanceOf(CustomException.class);
        SettingsUpdateRequest wrongGroup = new SettingsUpdateRequest();
        wrongGroup.setValues(Map.of("site_name_en", "x"));
        assertThatThrownBy(() -> systemSettingService.updateGroup("nope", wrongGroup)).isInstanceOf(CustomException.class);

        assertThat(allMyLogs()).isEqualTo(before);
    }

    // ---------------- passwords ----------------

    @Test
    void changingYourPassword_isLogged_withoutThePassword() throws Exception {
        loginAs(reader);
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("Old-Pass-1");
        request.setPassword("New-Pass-1");
        request.setConfirm("New-Pass-1");

        authService.changePassword(request);

        ActivityLog entry = one(ActivityAction.PASSWORD_CHANGED);
        assertSnapshot(entry, reader);
        assertThat(entry.getTargetType()).isEqualTo(ActivityTargetType.AUTH);
        assertThat(entry.getTargetId()).isEqualTo(reader.getId());
        assertThat(details(entry).get("method").asText()).isEqualTo("change");
        assertNoSecret(entry, "Old-Pass-1", "New-Pass-1");
    }

    @Test
    void aWrongCurrentPassword_changesNothing_andWritesNoEntry() {
        loginAs(reader);
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("Wrong-Pass-1");
        request.setPassword("New-Pass-1");
        request.setConfirm("New-Pass-1");
        long before = allMyLogs();

        assertThatThrownBy(() -> authService.changePassword(request)).isInstanceOf(CustomException.class);

        assertThat(allMyLogs()).isEqualTo(before);
        assertThat(logs(ActivityAction.PASSWORD_CHANGED)).isEmpty();
    }
}
