package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TagRequest {
    @NotBlank(message = "Name can not be blank")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    // optional: generated from the name when empty. Required when the name has no Latin letters (for example Khmer).
    @Size(max = 150, message = "Slug must be at most 150 characters")
    @Pattern(regexp = "^([a-z0-9]+(-[a-z0-9]+)*)?$", message = "Slug may only contain a-z, 0-9 and single hyphens")
    private String slug;
}
