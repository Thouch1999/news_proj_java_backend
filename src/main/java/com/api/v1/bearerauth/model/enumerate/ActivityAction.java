package com.api.v1.bearerauth.model.enumerate;

import java.util.EnumSet;
import java.util.Set;

/** What was done (the activity log). The content actions are the ones about articles and comments. */
public enum ActivityAction {
    NEWS_CREATED(true),
    NEWS_UPDATED(true),
    NEWS_SUBMITTED(true),
    NEWS_PUBLISHED(true),
    NEWS_REJECTED(true),
    NEWS_ARCHIVED(true),
    NEWS_RESTORED(true),
    NEWS_DELETED(true),
    COMMENT_HIDDEN(true),
    COMMENT_SHOWN(true),
    COMMENT_DELETED(true),
    COMMENTS_TOGGLED(true),
    USER_CREATED(false),
    USER_ACTIVATED(false),
    USER_DEACTIVATED(false),
    USER_ROLES_CHANGED(false),
    USER_PASSWORD_RESET(false),
    SUPER_ADMIN_TRANSFERRED(false),
    CATEGORY_CREATED(false),
    CATEGORY_UPDATED(false),
    CATEGORY_DELETED(false),
    TAG_CREATED(false),
    TAG_UPDATED(false),
    TAG_DELETED(false),
    SETTINGS_UPDATED(false),
    LOGIN_SUCCESS(false),
    LOGIN_FAILED(false),
    PASSWORD_CHANGED(false);

    /** The actions of the "Recent activity" card: articles and comments only. */
    public static final Set<ActivityAction> CONTENT_ACTIONS = EnumSet.noneOf(ActivityAction.class);

    static {
        for (ActivityAction action : values()) {
            if (action.content) {
                CONTENT_ACTIONS.add(action);
            }
        }
    }

    private final boolean content;

    ActivityAction(boolean content) {
        this.content = content;
    }

    public boolean isContent() {
        return content;
    }
}
