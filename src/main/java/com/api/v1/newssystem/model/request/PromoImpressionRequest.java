package com.api.v1.newssystem.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PromoImpressionRequest {
    // ARTICLE_SIDEBAR, ARTICLE_POPUP or MOBILE_BOTTOM_BAR (read in PromoService, so a wrong one is a 400)
    @NotBlank(message = "Placement can not be blank")
    private String placement;
}
