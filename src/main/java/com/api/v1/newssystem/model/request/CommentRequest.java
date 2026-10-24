package com.api.v1.newssystem.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentRequest {
    @NotBlank(message = "Comment can not be blank")
    @Size(max = 2000, message = "Comment must be at most 2000 characters")
    private String content;
}
