package dev.swirlit.indezy.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkFileLinksTest {

    private final NetworkFileLinks links = new NetworkFileLinks("secret");

    @Test
    void signsAddressesValidUntilTheEndOfTheNextHour() {
        Instant now = Instant.parse("2026-11-16T09:20:00Z");
        String url = links.url(7L, NetworkFileLinks.FULL, now);
        assertThat(url).startsWith("/public/network-files/7/full?e=" + Instant.parse("2026-11-16T11:00:00Z").getEpochSecond() + "&s=");
        assertThat(links.url(7L, NetworkFileLinks.FULL, now.plusSeconds(1800))).isEqualTo(url);
        long expires = Long.parseLong(url.substring(url.indexOf("e=") + 2, url.indexOf("&s=")));
        String signature = url.substring(url.indexOf("&s=") + 3);
        assertThat(links.valid(7L, NetworkFileLinks.FULL, expires, signature, now)).isTrue();
        assertThat(links.valid(8L, NetworkFileLinks.FULL, expires, signature, now)).isFalse();
        assertThat(links.valid(7L, NetworkFileLinks.PREVIEW, expires, signature, now)).isFalse();
        assertThat(links.valid(7L, NetworkFileLinks.FULL, expires + 3600, signature, now)).isFalse();
        assertThat(links.valid(7L, NetworkFileLinks.FULL, expires, signature, Instant.ofEpochSecond(expires))).isFalse();
        assertThat(links.valid(7L, NetworkFileLinks.FULL, expires, null, now)).isFalse();
        assertThat(new NetworkFileLinks("another").valid(7L, NetworkFileLinks.FULL, expires, signature, now)).isFalse();
    }
}
