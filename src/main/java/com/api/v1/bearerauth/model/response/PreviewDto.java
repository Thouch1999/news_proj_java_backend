package com.api.v1.bearerauth.model.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

// Everything the link-preview page (templates/preview/preview.html) shows. Plain text only: the template escapes it.
// Immutable, because it is kept in the "preview" cache.
@Value
@Builder
public class PreviewDto {
    String language;      // en or km
    String pageTitle;     // <title>: the title in the format of the setting title_format
    String title;         // og:title
    String description;   // plain text, at most about 200 characters; may be empty
    String ogType;        // article or website
    String siteName;
    String url;           // the canonical FRONTEND url (og:url)
    String locale;        // en_US or km_KH
    String imageUrl;      // absolute; null when the site has no image at all
    Integer imageWidth;   // null when not known
    Integer imageHeight;
    String imageAlt;
    String publishedTime; // ISO 8601, articles only
    String modifiedTime;
    String section;       // the category name, articles only
    List<String> tags;
}
