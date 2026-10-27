package com.api.v1.newssystem.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YouTubeUrlTest {
    private static final String ID = "dQw4w9WgXcQ";

    @Test
    void theFourAcceptedForms_giveTheVideoId() {
        for (String url : new String[]{
                "https://www.youtube.com/watch?v=" + ID,
                "https://youtube.com/watch?v=" + ID,
                "https://m.youtube.com/watch?v=" + ID,
                "http://www.youtube.com/watch?v=" + ID,
                "https://www.youtube.com/watch?feature=share&v=" + ID + "&t=42s",
                "https://youtu.be/" + ID,
                "https://youtu.be/" + ID + "?si=abc123",
                "https://youtu.be/" + ID + "/",
                "https://www.youtube.com/shorts/" + ID,
                "https://www.youtube.com/embed/" + ID,
                "  https://www.YouTube.com/watch?v=" + ID + "  "}) {
            assertThat(YouTubeUrl.videoId(url)).as(url).contains(ID);
        }
    }

    @Test
    void aLookAlikeDomain_isRefused() {
        for (String url : new String[]{
                "https://youtube.com.evil.example/watch?v=" + ID,
                "https://evil.example/youtube.com/watch?v=" + ID,
                "https://notyoutube.com/watch?v=" + ID,
                "https://youtube.co/watch?v=" + ID,
                "https://youtube.com@evil.example/watch?v=" + ID,
                "https://www.youtube.com@evil.example/" + ID,
                "https://user:pass@www.youtube.com/watch?v=" + ID,
                "https://evilyoutu.be/" + ID,
                "https://youtu.be.evil.example/" + ID,
                "https://www.youtube.com.evil.example/embed/" + ID,
                "https://www.youtube-nocookie.com/embed/" + ID,
                "https://music.youtube.com/watch?v=" + ID,
                "https://www.youtube.com:8080/watch?v=" + ID,
                "https://xn--youtube-9ta.com/watch?v=" + ID,
                "https://www.youtubе.com/watch?v=" + ID}) { // the last one has a Cyrillic "e" (U+0435)
            assertThat(YouTubeUrl.videoId(url)).as(url).isEmpty();
        }
    }

    @Test
    void aWrongSchemeOrShape_isRefused() {
        for (String url : new String[]{
                "javascript:alert(1)", "ftp://www.youtube.com/watch?v=" + ID, "//www.youtube.com/watch?v=" + ID,
                "www.youtube.com/watch?v=" + ID, ID, "", "   ", "not a url",
                "https://www.youtube.com/", "https://www.youtube.com/watch", "https://www.youtube.com/watch?v=",
                "https://www.youtube.com/watch?x=" + ID, "https://www.youtube.com/watch/" + ID,
                "https://www.youtube.com/playlist?list=PL123456789012", "https://www.youtube.com/user/" + ID,
                "https://www.youtube.com/shorts/", "https://www.youtube.com/shorts/" + ID + "/extra",
                "https://www.youtube.com/embed/" + ID + "/extra", "https://youtu.be/", "https://youtu.be/watch?v=" + ID,
                "https://youtu.be/" + ID + "/more"}) {
            assertThat(YouTubeUrl.videoId(url)).as(url).isEmpty();
        }
        assertThat(YouTubeUrl.videoId(null)).isEmpty();
    }

    @Test
    void theIdMustBeExactlyElevenSafeCharacters() {
        for (String id : new String[]{"short", "dQw4w9WgXc", "dQw4w9WgXcQQ", "dQw4w9WgXc!", "dQw4w9WgX<Q", "dQw4w9Wg Qa", "dQw4w9WgXc%"}) {
            assertThat(YouTubeUrl.videoId("https://www.youtube.com/watch?v=" + id)).as(id).isEmpty();
            assertThat(YouTubeUrl.videoId("https://youtu.be/" + id)).as(id).isEmpty();
        }
        assertThat(YouTubeUrl.videoId("https://youtu.be/a-_B1234567")).contains("a-_B1234567");
    }

    @Test
    void theThumbnail_isBuiltFromTheIdOnly() {
        assertThat(YouTubeUrl.thumbnailUrl(ID)).isEqualTo("https://i.ytimg.com/vi/" + ID + "/hqdefault.jpg");
    }
}
