package com.api.v1.newssystem.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum MessageConstant {

    BAD_REQUEST("BAD_REQUEST", HttpStatus.BAD_REQUEST, "Invalid request"),
    VALIDATION_FAILED("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "Validation failed"),
    INVALID_INPUT("INVALID_INPUT", HttpStatus.BAD_REQUEST, "Invalid input provided"),
    MALFORMED_JSON("MALFORMED_JSON", HttpStatus.BAD_REQUEST, "Malformed JSON request"),
    MISSING_PARAMETER("MISSING_PARAMETER", HttpStatus.BAD_REQUEST, "Required parameter is missing"),
    INVALID_PARAMETER("INVALID_PARAMETER", HttpStatus.BAD_REQUEST, "Invalid parameter value"),
    MISSING_HEADER("MISSING_HEADER", HttpStatus.BAD_REQUEST, "Required header is missing"),

    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, "Invalid refresh token"),
    TOKEN_EXPIRED("TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED, "Token has expired"),
    TOKEN_INVALID("TOKEN_INVALID", HttpStatus.UNAUTHORIZED, "Invalid token"),
    TOKEN_MISSING("TOKEN_MISSING", HttpStatus.UNAUTHORIZED, "Authentication token is missing"),

    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN, "Access denied"),
    INSUFFICIENT_PERMISSIONS("INSUFFICIENT_PERMISSIONS", HttpStatus.FORBIDDEN, "Insufficient permissions"),
    ACCOUNT_LOCKED("ACCOUNT_LOCKED", HttpStatus.FORBIDDEN, "Account is locked"),
    ACCOUNT_DISABLED("ACCOUNT_DISABLED", HttpStatus.FORBIDDEN, "Account is disabled"),

    NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND, "Resource not found"),
    USER_NOT_FOUND("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "User not found"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "Resource not found"),
    ENDPOINT_NOT_FOUND("ENDPOINT_NOT_FOUND", HttpStatus.NOT_FOUND, "Endpoint not found"),

    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not allowed"),
    NOT_ACCEPTABLE("NOT_ACCEPTABLE", HttpStatus.NOT_ACCEPTABLE, "Requested media type not acceptable"),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),

    CONFLICT("CONFLICT", HttpStatus.CONFLICT, "Resource conflict"),
    DUPLICATE_RESOURCE("DUPLICATE_RESOURCE", HttpStatus.CONFLICT, "Resource already exists"),
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT, "Email already registered"),
    USERNAME_ALREADY_EXISTS("USERNAME_ALREADY_EXISTS", HttpStatus.CONFLICT, "Username already taken"),

    TOO_MANY_REQUESTS("TOO_MANY_REQUESTS", HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please slow down"),

    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong"),
    DATABASE_ERROR("DATABASE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "Database error occurred"),
    EXTERNAL_SERVICE_ERROR("EXTERNAL_SERVICE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "External service failed"),

    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable"),

    CONFIRM_PASSWORD_NOT_MATCH("CONFIRM_PASSWORD_NOT_MATCH", HttpStatus.BAD_REQUEST, "Confirm password not match"),
    CANNOT_DEACTIVATE_SELF("CANNOT_DEACTIVATE_SELF", HttpStatus.BAD_REQUEST, "You cannot deactivate your own account"),
    CANNOT_REMOVE_OWN_ADMIN_ROLE("CANNOT_REMOVE_OWN_ADMIN_ROLE", HttpStatus.BAD_REQUEST, "You cannot remove your own ADMIN role"),
    SUPER_ADMIN_ONLY("SUPER_ADMIN_ONLY", HttpStatus.FORBIDDEN, "Only the super admin can do this"),
    SUPER_ADMIN_CANNOT_BE_DEACTIVATED("SUPER_ADMIN_CANNOT_BE_DEACTIVATED", HttpStatus.FORBIDDEN, "The super admin cannot be deactivated"),
    SUPER_ADMIN_PASSWORD_PROTECTED("SUPER_ADMIN_PASSWORD_PROTECTED", HttpStatus.FORBIDDEN, "The password of the super admin cannot be reset by someone else"),
    SUPER_ADMIN_ROLE_PROTECTED("SUPER_ADMIN_ROLE_PROTECTED", HttpStatus.FORBIDDEN, "The roles of the super admin cannot be changed"),
    LAST_ADMIN_REQUIRED("LAST_ADMIN_REQUIRED", HttpStatus.CONFLICT, "The system must keep at least one active admin"),
    SUPER_ADMIN_TRANSFER_INVALID_TARGET("SUPER_ADMIN_TRANSFER_INVALID_TARGET", HttpStatus.BAD_REQUEST, "The new super admin must be another active admin with a verified email"),
    CATEGORY_NOT_FOUND("CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "Category not found"),
    TAG_NOT_FOUND("TAG_NOT_FOUND", HttpStatus.NOT_FOUND, "Tag not found"),
    CATEGORY_NAME_EXISTS("CATEGORY_NAME_EXISTS", HttpStatus.CONFLICT, "A category with this name already exists"),
    TAG_NAME_EXISTS("TAG_NAME_EXISTS", HttpStatus.CONFLICT, "A tag with this name already exists"),
    SLUG_ALREADY_EXISTS("SLUG_ALREADY_EXISTS", HttpStatus.CONFLICT, "This slug is already in use"),
    SLUG_REQUIRED("SLUG_REQUIRED", HttpStatus.BAD_REQUEST, "A slug is required for this name: use letters a-z, digits and hyphens"),
    CATEGORY_HAS_NEWS("CATEGORY_HAS_NEWS", HttpStatus.CONFLICT, "This category has news and cannot be deleted; deactivate it instead"),
    NEWS_NOT_FOUND("NEWS_NOT_FOUND", HttpStatus.NOT_FOUND, "News not found"),
    REPORT_RANGE_TOO_LONG("REPORT_RANGE_TOO_LONG", HttpStatus.BAD_REQUEST, "The date range is too long for this report (at most 366 days or 60 months)"),
    COMMENT_NOT_FOUND("COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "Comment not found"),
    COMMENTS_DISABLED("COMMENTS_DISABLED", HttpStatus.CONFLICT, "Comments are disabled for this article"),
    NEWS_IMAGE_NOT_FOUND("NEWS_IMAGE_NOT_FOUND", HttpStatus.NOT_FOUND, "Image not found"),
    NEWS_NOT_EDITABLE("NEWS_NOT_EDITABLE", HttpStatus.CONFLICT, "This article cannot be edited in its current status"),
    NEWS_INVALID_TRANSITION("NEWS_INVALID_TRANSITION", HttpStatus.CONFLICT, "This action is not allowed for the article's current status"),
    NEWS_ACTION_NOT_ALLOWED("NEWS_ACTION_NOT_ALLOWED", HttpStatus.FORBIDDEN, "Your role is not allowed to perform this action"),
    NEWS_DELETE_ONLY_DRAFT("NEWS_DELETE_ONLY_DRAFT", HttpStatus.CONFLICT, "Only a DRAFT article can be deleted"),
    NEWS_IMAGE_LIMIT("NEWS_IMAGE_LIMIT", HttpStatus.CONFLICT, "This article already has the maximum number of images"),
    CATEGORY_INACTIVE("CATEGORY_INACTIVE", HttpStatus.BAD_REQUEST, "This category is not active"),
    FILE_EMPTY("FILE_EMPTY", HttpStatus.BAD_REQUEST, "The file is missing or empty"),
    FILE_TOO_LARGE("FILE_TOO_LARGE", HttpStatus.BAD_REQUEST, "The file is too large"),
    FILE_TYPE_NOT_ALLOWED("FILE_TYPE_NOT_ALLOWED", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only JPEG, PNG, GIF and WebP images are allowed"),
    FILE_STORAGE_ERROR("FILE_STORAGE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "The file could not be stored"),
    SETTING_GROUP_UNKNOWN("SETTING_GROUP_UNKNOWN", HttpStatus.NOT_FOUND, "Unknown settings group"),
    SETTING_IMAGE_TYPE_UNKNOWN("SETTING_IMAGE_TYPE_UNKNOWN", HttpStatus.NOT_FOUND, "Unknown settings image"),
    SETTING_IMAGE_TYPE_NOT_ALLOWED("SETTING_IMAGE_TYPE_NOT_ALLOWED", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PNG, JPG and WebP images are allowed (a favicon may also be ICO)"),
    SETTING_IMAGE_TOO_LARGE("SETTING_IMAGE_TOO_LARGE", HttpStatus.BAD_REQUEST, "The image is too large (2 MB at most)"),
    SPONSOR_NOT_FOUND("SPONSOR_NOT_FOUND", HttpStatus.NOT_FOUND, "Sponsor not found"),
    SPONSOR_CREATIVE_NOT_FOUND("SPONSOR_CREATIVE_NOT_FOUND", HttpStatus.NOT_FOUND, "The sponsor has no image for this placement"),
    SPONSOR_IMAGE_IN_USE("SPONSOR_IMAGE_IN_USE", HttpStatus.BAD_REQUEST, "This image is used by a chosen place: uncheck the place first"),
    SPONSOR_PLACEMENT_INVALID("SPONSOR_PLACEMENT_INVALID", HttpStatus.BAD_REQUEST, "Unknown placement"),
    SPONSOR_STATUS_INVALID("SPONSOR_STATUS_INVALID", HttpStatus.BAD_REQUEST, "Status must be active, scheduled, ended or inactive"),
    SPONSOR_STATS_RANGE_INVALID("SPONSOR_STATS_RANGE_INVALID", HttpStatus.BAD_REQUEST, "The date range is not valid (from must not be after to, 366 days at most)"),
    SPONSOR_IMAGE_TYPE_NOT_ALLOWED("SPONSOR_IMAGE_TYPE_NOT_ALLOWED", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PNG, JPG and WebP images are allowed"),
    SPONSOR_IMAGE_TOO_LARGE("SPONSOR_IMAGE_TOO_LARGE", HttpStatus.BAD_REQUEST, "The image is too large (2 MB at most)"),
    SPONSOR_VIDEO_TYPE_NOT_ALLOWED("SPONSOR_VIDEO_TYPE_NOT_ALLOWED", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only MP4 (H.264) and WebM videos are allowed"),
    SPONSOR_VIDEO_TOO_LARGE("SPONSOR_VIDEO_TOO_LARGE", HttpStatus.BAD_REQUEST, "The video is too large (15 MB at most)"),
    SPONSOR_VIDEO_TOO_LONG("SPONSOR_VIDEO_TOO_LONG", HttpStatus.BAD_REQUEST, "The video is too long (30 seconds at most)"),
    SPONSOR_VIDEO_NOT_FOUND("SPONSOR_VIDEO_NOT_FOUND", HttpStatus.NOT_FOUND, "The sponsor has no video for this placement"),
    SPONSOR_LOGO_NOT_FOUND("SPONSOR_LOGO_NOT_FOUND", HttpStatus.NOT_FOUND, "The sponsor has no logo"),
    SPONSOR_MEDIA_NOT_ALLOWED("SPONSOR_MEDIA_NOT_ALLOWED", HttpStatus.BAD_REQUEST, "This kind of media can not be used in this placement"),
    NOTIFICATION_NOT_FOUND("NOTIFICATION_NOT_FOUND", HttpStatus.NOT_FOUND, "Notification not found"),
    ACTIVITY_LOG_NOT_FOUND("ACTIVITY_LOG_NOT_FOUND", HttpStatus.NOT_FOUND, "Activity log entry not found"),
    CURRENT_PASSWORD_INCORRECT("CURRENT_PASSWORD_INCORRECT", HttpStatus.BAD_REQUEST, "Current password is incorrect"),

    OTP_EXPIRED("OTP_EXPIRED", HttpStatus.BAD_REQUEST, "OTP has expired or was never requested"),
    OTP_INVALID("OTP_INVALID", HttpStatus.BAD_REQUEST, "Invalid OTP"),
    OTP_ALREADY_USED("OTP_ALREADY_USED", HttpStatus.BAD_REQUEST, "OTP has already been used"),
    OTP_ATTEMPTS_EXCEEDED("OTP_ATTEMPTS_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS, "Too many wrong attempts, please request a new OTP"),
    OTP_RESEND_COOLDOWN("OTP_RESEND_COOLDOWN", HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another OTP"),
    OTP_RESEND_LIMIT_EXCEEDED("OTP_RESEND_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS, "OTP request limit reached, please try again later"),
    EMAIL_NOT_VERIFIED("EMAIL_NOT_VERIFIED", HttpStatus.FORBIDDEN, "Email has not been verified by OTP"),

    RATE_LIMITED("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please try again later"),
    ACCOUNT_TEMPORARILY_LOCKED("ACCOUNT_TEMPORARILY_LOCKED", HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts, please try again later"),
    COMMENT_TOO_MANY_LINKS("COMMENT_TOO_MANY_LINKS", HttpStatus.BAD_REQUEST, "A comment can have at most 2 links"),
    COMMENT_DUPLICATE("COMMENT_DUPLICATE", HttpStatus.CONFLICT, "You already posted this comment"),
    ACCOUNT_TOO_NEW("ACCOUNT_TOO_NEW", HttpStatus.FORBIDDEN, "Your account is too new to comment, please try again in a few minutes"),
    COMMENT_AWAITING_REVIEW("COMMENT_AWAITING_REVIEW", HttpStatus.ACCEPTED, "Your comment is waiting for review.");

    private final String code;
    private final HttpStatus status;
    private final String defaultMessage;
}