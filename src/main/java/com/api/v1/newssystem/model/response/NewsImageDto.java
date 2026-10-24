package com.api.v1.newssystem.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NewsImageDto {
    private Long id;
    // public path on this server, for example /uploads/<uuid>.png
    private String url;
    private String caption;
    private int sortOrder;
}
