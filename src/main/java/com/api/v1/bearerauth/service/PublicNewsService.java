package com.api.v1.bearerauth.service;

import com.api.v1.bearerauth.model.entity.News;
import com.api.v1.bearerauth.model.response.PublicCategorySectionDto;
import com.api.v1.bearerauth.model.response.PublicNewsDto;
import com.api.v1.bearerauth.model.response.PublicNewsSummaryDto;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only news for readers (no login). Only PUBLISHED articles of an active category are ever returned;
 * anything else is "not found".
 */
public interface PublicNewsService {
    /**
     * Published news, newest first; page is 1-based. Every filter is optional: `keyword` is searched in the title,
     * summary and content (any language, also Khmer), `category` and `tag` are slugs, and `from` / `to` are
     * publish dates (both included). A null `size` is the page size of the system settings (news_per_page).
     */
    Page<PublicNewsSummaryDto> list(String keyword, String category, String tag, LocalDate from, LocalDate to, int page, Integer size);

    /**
     * One article by slug, with category, tags, author name, images, view count and comment count. Opening it records
     * a view: once per logged-in user, or once per address for a visitor, in any 24 hours. `ip` is the visitor's address.
     * For a logged-in user the result also says whether they saved the article (`bookmarked`); for a visitor it is null.
     */
    PublicNewsDto getBySlug(String slug, String ip);

    /**
     * Cards for articles that are already loaded together with their author and category (for example the saved news of
     * a user). Tags and comment counts of all of them are loaded with one query each. Keeps the order.
     */
    List<PublicNewsSummaryDto> toCards(List<News> news);

    /** The newest published articles. */
    List<PublicNewsSummaryDto> latest(int limit);

    /** The published articles with the most views. */
    List<PublicNewsSummaryDto> mostViewed(int limit);

    /** For the home page: every active category that has news, with its newest `perCategory` articles. */
    List<PublicCategorySectionDto> categorySections(int perCategory);
}
