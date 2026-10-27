package dev.swirlit.indezy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Where webhooks may post: HTTPS addresses of public hosts only, so a webhook cannot reach the cluster's own services
 * (server-side request forgery). The host is checked when the webhook is written and resolved again before each
 * delivery; loopback, private, link-local, carrier-grade NAT, unique-local and multicast addresses are refused.
 * {@code indezy.webhooks.allow-private-addresses} lifts it for tests, which post to a local receiver.
 */
@Component
public class WebhookAddresses {

    private final boolean allowPrivate;

    public WebhookAddresses(@Value("${indezy.webhooks.allow-private-addresses:false}") boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
    }

    /** The address, refused when it is not HTTPS, has credentials or a fragment, or names a non-public host. */
    public URI requireValid(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("The webhook address is not a valid URL");
        }
        boolean secure = "https".equalsIgnoreCase(uri.getScheme()) || allowPrivate && "http".equalsIgnoreCase(uri.getScheme());
        if (!secure || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("A webhook address is an https:// URL without credentials");
        }
        requirePublic(uri.getHost());
        return uri;
    }

    /** Refuses a host that resolves, even in part, to a non-public address. */
    public void requirePublic(String host) {
        if (allowPrivate) {
            return;
        }
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("The webhook host does not resolve");
        }
        for (InetAddress address : resolved) {
            if (!isPublic(address)) {
                throw new IllegalArgumentException("A webhook cannot post to a private or local address");
            }
        }
    }

    static boolean isPublic(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress() ||
            address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // 100.64.0.0/10 (carrier-grade NAT, also used inside clusters) and 0.0.0.0/8.
            return !(first == 100 && second >= 64 && second <= 127) && first != 0;
        }
        // fc00::/7 (unique local) and IPv4-mapped addresses are judged by what they map to.
        if ((bytes[0] & 0xFE) == 0xFC) {
            return false;
        }
        return true;
    }
}
