package dev.swirlit.indezy.scraping;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * The hosts a pasted link may make the server read: names on the public internet only. IP literals, single-label names
 * and private suffixes (a cluster's own services, a home network) are refused, and so is a name resolving to a loopback,
 * private, link-local, carrier-grade NAT, unique-local, multicast or unspecified address, so a link can reach neither the
 * cluster nor a cloud's metadata service (server-side request forgery). Every host of a reading is checked, redirects
 * included (see {@link SiteHttpClient#fetcherFor(String, java.util.function.Predicate)}).
 */
public final class PublicHosts {

    /** Resolves a host name; replaced in tests. */
    @FunctionalInterface
    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private static final List<String> PRIVATE_SUFFIXES = List.of(".internal", ".local", ".localhost", ".localdomain", ".lan",
        ".home", ".corp", ".intranet", ".private", ".svc", ".cluster.local", ".home.arpa", ".test", ".invalid", ".example");
    private static final int IPV4_BYTES = 4;
    private static final int CGNAT_FIRST = 100;
    private static final int CGNAT_SECOND_MIN = 64;
    private static final int CGNAT_SECOND_MAX = 127;
    private static final int UNIQUE_LOCAL_MASK = 0xfe;
    private static final int UNIQUE_LOCAL_PREFIX = 0xfc;

    private PublicHosts() {
    }

    /** Whether the server may read {@code host}, resolved by the system's resolver. */
    public static boolean isPublic(String host) {
        return isPublic(host, InetAddress::getAllByName);
    }

    /** Whether the server may read {@code host}: a public name, every address of which is public. */
    public static boolean isPublic(String host, Resolver resolver) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String name = host.toLowerCase(Locale.ROOT);
        if (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        if (!name.contains(".") || name.contains(":") || name.matches("[0-9.]+") || PRIVATE_SUFFIXES.stream().anyMatch(name::endsWith)) {
            return false;
        }
        try {
            InetAddress[] addresses = resolver.resolve(name);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** Whether an address is on the public internet. */
    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() ||
            address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == IPV4_BYTES) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            // 0.0.0.0/8 and 100.64.0.0/10 (carrier-grade NAT, which clusters use for their pods).
            return first != 0 && !(first == CGNAT_FIRST && second >= CGNAT_SECOND_MIN && second <= CGNAT_SECOND_MAX);
        }
        // fc00::/7, IPv6's private range.
        return !(address instanceof Inet6Address) || (bytes[0] & UNIQUE_LOCAL_MASK) != UNIQUE_LOCAL_PREFIX;
    }
}
