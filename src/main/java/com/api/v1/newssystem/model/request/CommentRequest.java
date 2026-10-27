package com.api.v1.newssystem.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentRequest {
    @NotBlank(message = "Comment can not be blank")
    @Size(max = 2000, message = "Comment must be at most 2000 characters")
    private String content;

    // honeypot: a hidden field that people never fill in; when it has a value the request is ignored (see the protection rules)
    @Size(max = 255, message = "Website must be at most 255 characters")
    private String website;
}
