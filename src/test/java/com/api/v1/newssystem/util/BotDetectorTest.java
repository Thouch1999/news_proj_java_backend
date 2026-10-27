package com.api.v1.newssystem.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BotDetectorTest {

    @Test
    void knownCrawlersAndScripts_areBots() {
        assertThat(BotDetector.isBot("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")).isTrue();
        assertThat(BotDetector.isBot("facebookexternalhit/1.1")).isTrue();
        assertThat(BotDetector.isBot("curl/8.4.0")).isTrue();
        assertThat(BotDetector.isBot("python-requests/2.31")).isTrue();
        assertThat(BotDetector.isBot("Mozilla/5.0 HeadlessChrome/120.0")).isTrue();
        assertThat(BotDetector.isBot("Mozilla/5.0 (compatible; bingbot/2.0)")).isTrue();
    }

    @Test
    void noUserAgent_isABot() {
        assertThat(BotDetector.isBot(null)).isTrue();
        assertThat(BotDetector.isBot("  ")).isTrue();
    }

    @Test
    void realBrowsers_areNot() {
        assertThat(BotDetector.isBot("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")).isFalse();
        assertThat(BotDetector.isBot("Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1")).isFalse();
        assertThat(BotDetector.isBot("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")).isFalse();
        assertThat(BotDetector.isBot("Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0")).isFalse();
    }
}
