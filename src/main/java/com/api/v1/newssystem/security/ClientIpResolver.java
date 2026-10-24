package com.api.v1.newssystem.security;

import com.api.v1.newssystem.config.AppProperty;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The address of the client of a request. The address of the connection is used, unless it is one of the reverse proxies
 * in app.activity-log.trusted-proxies: only then the header X-Forwarded-For is believed, and the client is the first
 * address from the right that is not a trusted proxy (an address further left was written by the client and can be
 * anything). Without trusted proxies the header is ignored, so nobody can write a false address in the log.
 */
@Component
@Slf4j
public class ClientIpResolver {
    private static final int MAX_LENGTH = 45; // the longest IPv6 text
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]{2,45}");

    private final List<IpAddressMatcher> trusted;

    public ClientIpResolver(AppProperty appProperty) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        for (String entry : appProperty.getActivityLog().getTrustedProxies()) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            try {
                matchers.add(new IpAddressMatcher(entry.trim()));
            } catch (IllegalArgumentException e) {
                log.warn("Trusted proxy ignored (not an address or a CIDR range): {}", entry);
            }
        }
        this.trusted = Collections.unmodifiableList(matchers);
    }

    /** The client address, or null when there is no request (a scheduled job). */
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String remote = request.getRemoteAddr();
        if (remote == null || trusted.isEmpty() || !isTrusted(remote)) {
            return cut(remote);
        }
        List<String> hops = new ArrayList<>();
        var headers = request.getHeaders("X-Forwarded-For");
        while (headers != null && headers.hasMoreElements()) {
            for (String hop : headers.nextElement().split(",")) {
                hops.add(hop.trim());
            }
        }
        for (int i = hops.size() - 1; i >= 0; i--) {
            String hop = hops.get(i);
            if (!isAddress(hop)) {
                return cut(remote); // a malformed header is not believed
            }
            if (!isTrusted(hop)) {
                return hop;
            }
        }
        return cut(remote); // every hop is a trusted proxy: the connection is the best we know
    }

    private boolean isTrusted(String address) {
        if (!isAddress(address)) {
            return false;
        }
        for (IpAddressMatcher matcher : trusted) {
            if (matcher.matches(address)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAddress(String text) {
        if (text == null || text.length() > MAX_LENGTH) {
            return false;
        }
        return IPV4.matcher(text).matches() || (text.indexOf(':') >= 0 && IPV6.matcher(text).matches());
    }

    private static String cut(String text) {
        return text == null || text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
    }
}
