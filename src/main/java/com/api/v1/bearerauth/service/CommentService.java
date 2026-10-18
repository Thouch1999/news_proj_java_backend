package com.api.v1.bearerauth.service;

import com.api.v1.bearerauth.model.enumerate.CommentStatus;
import com.api.v1.bearerauth.model.request.CommentRequest;
import com.api.v1.bearerauth.model.response.CommentDto;
import org.springframework.data.domain.Page;

/**
 * Comments on articles.
 * <ul>
 *   <li>Everybody reads the VISIBLE comments of a PUBLISHED article.</li>
 *   <li>A logged-in user comments on a PUBLISHED article that has comments enabled, and deletes their own comments.</li>
 *   <li>An EDITOR or ADMIN lists all comments, hides / unhides them and deletes any comment.</li>
 * </ul>
 */
public interface CommentService {
    /** VISIBLE comments of a published article, newest first; page is 1-based. 404 when the article is not published. */
    Page<CommentDto> listVisible(Long newsId, int page, int size);

    /** Adds a VISIBLE comment by the logged-in user. 404: article not published; 409: comments are disabled. */
    CommentDto create(Long newsId, CommentRequest request);

    /** The comment's author, or an editor / admin. Removed for good. */
    void delete(Long id);

    /** EDITOR/ADMIN: every comment, newest first; each filter is optional. */
    Page<CommentDto> listAll(CommentStatus status, Long newsId, Long userId, int page, int size);

    /** EDITOR/ADMIN: VISIBLE -> HIDDEN (hiding a hidden comment changes nothing). */
    CommentDto hide(Long id);

    /** EDITOR/ADMIN: HIDDEN -> VISIBLE (unhiding a visible comment changes nothing). */
    CommentDto unhide(Long id);
}
