package com.api.v1.bearerauth.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// A home page section: a category with its newest articles.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PublicCategorySectionDto {
    private CategoryDto category;
    private List<PublicNewsSummaryDto> news;
}
