package com.api.v1.bearerauth.service;

import com.api.v1.bearerauth.model.enumerate.NewsStatus;
import com.api.v1.bearerauth.model.request.NewsImageRequest;
import com.api.v1.bearerauth.model.request.NewsRequest;
import com.api.v1.bearerauth.model.response.NewsDto;
import com.api.v1.bearerauth.model.response.NewsImageDto;
import com.api.v1.bearerauth.model.response.NewsSummaryDto;
import com.api.v1.bearerauth.model.response.NewsStatusHistoryDto;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Article management for AUTHOR, EDITOR and ADMIN.
 * <ul>
 *   <li>An author works on their own articles only (another author's article is "not found") and may edit them
 *       only while they are DRAFT or REJECTED; only an own DRAFT can be deleted.</li>
 *   <li>An editor sees every article and edits any that is not ARCHIVED.</li>
 *   <li>An admin sees, edits and deletes every article of every author, in any status.</li>
 * </ul>
 */
public interface NewsService {
    /** Creates the article as DRAFT; the logged-in user is the author. */
    NewsDto create(NewsRequest request);

    NewsDto update(Long id, NewsRequest request);

    /** One of the logged-in user's own articles, in any status. */
    NewsDto getMine(Long id);

    /** One article: an author gets their own, an admin gets any. */
    NewsDto getManaged(Long id);

    /** The logged-in user's own articles, newest change first; page is 1-based and the status filter is optional. */
    Page<NewsSummaryDto> listMine(NewsStatus status, int page, int size);

    /**
     * Management list. An admin gets every article and may filter by status, author and category;
     * an author always gets only their own (authorId is ignored) and may filter by status and category.
     */
    Page<NewsSummaryDto> listManaged(NewsStatus status, Long authorId, Long categoryId, int page, int size);

    /**
     * Hard-deletes an article with everything attached to it: tags, images (and their files), status history,
     * comments, bookmarks and views. An author can delete an own DRAFT only; an admin any article.
     */
    void delete(Long id);

    NewsImageDto addImage(Long newsId, MultipartFile file, String caption);

    NewsImageDto updateImage(Long newsId, Long imageId, NewsImageRequest request);

    void deleteImage(Long newsId, Long imageId);

    NewsDto setThumbnail(Long newsId, MultipartFile file);

    NewsDto removeThumbnail(Long newsId);

    // ---- editorial workflow (all transition rules live in NewsServiceImpl) ----

    /** Own article, DRAFT or REJECTED → PENDING; clears the rejection reason. */
    NewsDto submit(Long id);

    /** EDITOR/ADMIN: PENDING → PUBLISHED. ADMIN only: DRAFT → PUBLISHED (without review). */
    NewsDto publish(Long id);

    /** EDITOR/ADMIN: PENDING → REJECTED; the reason is required and saved on the article. */
    NewsDto reject(Long id, String reason);

    /** EDITOR/ADMIN: PUBLISHED → ARCHIVED. */
    NewsDto archive(Long id);

    /** ADMIN only: ARCHIVED → PUBLISHED. */
    NewsDto restore(Long id);

    /** EDITOR/ADMIN: allow or stop new comments on an article, in any status. Existing comments stay. */
    NewsDto setCommentsEnabled(Long id, boolean enabled);

    /** EDITOR/ADMIN: the PENDING articles, oldest first; page is 1-based. */
    Page<NewsSummaryDto> listReviewQueue(int page, int size);

    /** Status changes of an article, oldest first; an author sees their own articles only. */
    List<NewsStatusHistoryDto> history(Long id);
}
