package com.api.v1.newssystem.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A YouTube link that passed the check: the video ID that would be stored and its thumbnail. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class YoutubeCheckDto {
    private String videoId;
    private String thumbnailUrl;
}
