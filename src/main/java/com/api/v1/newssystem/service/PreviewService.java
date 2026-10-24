package com.api.v1.newssystem.service;

import com.api.v1.newssystem.model.response.PreviewDto;

import java.util.Optional;

/**
 * The data of the link-preview pages (Open Graph) that Facebook, Telegram, X ... read when a link is shared.
 * Only what is public is ever returned, and reading a preview never counts as a view of the article.
 * `lang` is en or km; anything else (or null) means the setting default_language.
 */
public interface PreviewService {
    /** The preview of a PUBLISHED article; empty for a draft, pending, rejected, archived or unknown article. Cached. */
    Optional<PreviewDto> news(String slug, String lang);

    /** The preview of an active category; empty when it does not exist. Cached. */
    Optional<PreviewDto> category(String slug, String lang);

    /** The preview of the home page, also what an unknown link shows. Cached. */
    PreviewDto home(String lang);

    /** Forgets the cached previews of an article (both languages) once the running transaction has committed. */
    void evictNews(String slug);
}
