package dev.swirlit.indezy.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * The address a request came from. Behind the cluster's ingress the connection comes from a private address, and the
 * ingress appends the client's to {@code X-Forwarded-For}: its last entry is the one to trust, since a client can send
 * that header with any first entries.
 */
public final class ClientAddress {

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank() || !isProxy(remote)) {
            return remote;
        }
        String[] hops = forwarded.split(",");
        String last = hops[hops.length - 1].strip();
        return last.isEmpty() ? remote : last;
    }

    private static boolean isProxy(String address) {
        if (address == null || !address.matches("[0-9a-fA-F:.]+")) {
            return false;
        }
        try {
            InetAddress parsed = InetAddress.getByName(address);
            byte[] bytes = parsed.getAddress();
            boolean cgnat = bytes.length == 4 && (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64;
            return parsed.isLoopbackAddress() || parsed.isSiteLocalAddress() || cgnat;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
