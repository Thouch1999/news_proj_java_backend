package com.api.v1.newssystem.service;

import com.api.v1.newssystem.model.response.BookmarkDto;
import org.springframework.data.domain.Page;

/** Saved news of the logged-in user (any role). */
public interface BookmarkService {
    /**
     * Saves a PUBLISHED article (in an active category) for the logged-in user. Saving it again changes nothing and
     * never creates a duplicate. Returns true when a bookmark was created, false when it already existed.
     * 404 when the article is not published.
     */
    boolean add(Long newsId);

    /** Removes the bookmark; nothing happens when there is none (also for an article that is no longer published). */
    void remove(Long newsId);

    /** The user's saved news that readers can open, newest bookmark first; page is 1-based. */
    Page<BookmarkDto> list(int page, int size);
}
