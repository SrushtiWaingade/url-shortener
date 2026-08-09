package com.example.shortener.util;

import com.example.shortener.exception.InvalidUrlException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UrlValidator {

    private static final Pattern IPV4 =
            Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    private static final Set<String> BLOCKED_SUFFIXES = Set.of(".local", ".internal", ".localhost");

    private UrlValidator() {
    }

    // Length and is blank  handled by validation on request DTO
    public static void validate(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("not a valid URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidUrlException("only http and https URLs are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL must have a host");
        }

        // https://paypal.com@evil.com looks like paypal but goes to evil.com
        if (uri.getUserInfo() != null) {
            throw new InvalidUrlException("URLs containing user info are not allowed");
        }

        if (isLocalOrPrivate(host)) {
            throw new InvalidUrlException("URLs pointing at local or private addresses "
                    + "are not allowed");
        }
    }

    private static boolean isLocalOrPrivate(String host) {
        String h = host.toLowerCase();

        if (h.equals("localhost") || h.equals("::1") || h.equals("[::1]")) {
            return true;
        }
        if (BLOCKED_SUFFIXES.stream().anyMatch(h::endsWith)) {
            return true;
        }
        return isPrivateIpv4(h);
    }

    private static boolean isPrivateIpv4(String host) {
        Matcher m = IPV4.matcher(host);
        if (!m.matches()) {
            return false;
        }

        int first = Integer.parseInt(m.group(1));
        int second = Integer.parseInt(m.group(2));

        return first == 0
                || first == 10
                || first == 127
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 169 && second == 254);
    }
}