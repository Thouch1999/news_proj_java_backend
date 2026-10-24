package com.api.v1.newssystem.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class NewsRequest {
    @NotBlank(message = "Title can not be blank")
    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;

    @Size(max = 1000, message = "Summary must be at most 1000 characters")
    private String summary;

    // a draft may still be empty
    @Size(max = 500000, message = "Content is too long")
    private String content;

    @NotNull(message = "Category can not be null")
    private Long categoryId;

    // on update: null leaves the tags as they are, an empty list removes them all
    @Size(max = 20, message = "An article can have at most 20 tags")
    private List<@NotNull(message = "Tag id can not be null") Long> tagIds;

    // null keeps the current value (true for a new article)
    private Boolean commentsEnabled;
}
