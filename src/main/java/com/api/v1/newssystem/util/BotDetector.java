package com.api.v1.newssystem.util;

import java.util.regex.Pattern;

/**
 * Tells known crawlers and scripts from browsers by the User-Agent, so they do not inflate the sponsor numbers. A request
 * without a User-Agent counts as a bot too: every browser sends one.
 */
public final class BotDetector {
    private static final Pattern BOT = Pattern.compile(
            "bot|crawl|spider|slurp|scrape|fetch|headless|phantom|preview|facebookexternalhit|embedly|whatsapp|telegram"
                    + "|curl|wget|python|java/|okhttp|httpclient|go-http|libwww|axios|node-fetch|monitor|uptime|lighthouse|pingdom",
            Pattern.CASE_INSENSITIVE);

    private BotDetector() {
    }

    public static boolean isBot(String userAgent) {
        return userAgent == null || userAgent.isBlank() || BOT.matcher(userAgent).find();
    }
}
