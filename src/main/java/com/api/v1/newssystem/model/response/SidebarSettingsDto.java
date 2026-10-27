package com.api.v1.newssystem.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The display settings of the sidebar slideshow (always valid: inside the limits). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SidebarSettingsDto {
    // LIST_1, GRID_2 or LOGOS_4
    private String layout;
    // sponsors per slide
    private int perSlide;
    private boolean rotate;
    private int intervalSeconds;
}
