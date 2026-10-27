package com.api.v1.newssystem.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The answer for the article sidebar: the display settings and up to 24 different sponsors in weighted-random order. The
 * page splits `items` into slides of `perSlide`; `imageUrl` of an item is the image of the layout (the sidebar picture, or the
 * square logo for LOGOS_4).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromoSidebarDto {
    private String layout;
    private int perSlide;
    private boolean rotate;
    private int intervalSeconds;
    private List<PromoDto> items;
}
