package dev.swirlit.indezy.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;

/** Names the browser and system of a request from its User-Agent ("Firefox on Windows"), without keeping the header. */
public final class RequestDevice {

    private static final String[][] BROWSERS = {
        {"edg/", "Edge"}, {"opr/", "Opera"}, {"firefox/", "Firefox"}, {"chrome/", "Chrome"}, {"chromium/", "Chromium"},
        {"safari/", "Safari"}, {"curl/", "curl"}, {"postman", "Postman"}
    };
    private static final String[][] SYSTEMS = {
        {"android", "Android"}, {"iphone", "iPhone"}, {"ipad", "iPad"}, {"windows", "Windows"}, {"mac os x", "macOS"},
        {"cros", "ChromeOS"}, {"linux", "Linux"}
    };

    private RequestDevice() {
    }

    /** The device of the request being served, or null outside one. */
    public static String current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return describe(attributes.getRequest().getHeader("User-Agent"));
        }
        return null;
    }

    public static String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String agent = userAgent.toLowerCase(Locale.ROOT);
        String browser = find(BROWSERS, agent);
        String system = find(SYSTEMS, agent);
        if (browser == null && system == null) {
            return "Other";
        }
        if (browser == null || system == null) {
            return browser == null ? system : browser;
        }
        return browser + " on " + system;
    }

    private static String find(String[][] names, String agent) {
        for (String[] name : names) {
            if (agent.contains(name[0])) {
                return name[1];
            }
        }
        return null;
    }
}
