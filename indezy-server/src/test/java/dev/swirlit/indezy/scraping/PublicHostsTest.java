package dev.swirlit.indezy.scraping;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PublicHostsTest {

    /** Resolves names as a table says, and nothing else. */
    private static PublicHosts.Resolver table(Map<String, String> addresses) {
        return host -> {
            String address = addresses.get(host);
            if (address == null) {
                throw new UnknownHostException(host);
            }
            String[] all = address.split(",");
            InetAddress[] resolved = new InetAddress[all.length];
            for (int i = 0; i < all.length; i++) {
                resolved[i] = InetAddress.getByName(all[i]);
            }
            return resolved;
        };
    }

    @Test
    void readsNamesOfThePublicInternetOnly() {
        PublicHosts.Resolver resolver = table(Map.of(
            "www.welcometothejungle.com", "52.95.110.1",
            "jobs.example.org", "2606:4700::6810:85e5",
            "intranet.acme.com", "10.0.0.12",
            "loop.acme.com", "127.0.0.1",
            "pods.acme.com", "100.96.3.4",
            "metadata.acme.com", "169.254.169.254",
            "ula.acme.com", "fd12:3456::1",
            "split.acme.com", "52.95.110.1,192.168.1.10",
            "zero.acme.com", "0.0.0.0"));

        assertThat(PublicHosts.isPublic("www.welcometothejungle.com", resolver)).isTrue();
        assertThat(PublicHosts.isPublic("WWW.WelcomeToTheJungle.com.", resolver)).isTrue();
        assertThat(PublicHosts.isPublic("jobs.example.org", resolver)).isTrue();
        for (String refused : new String[] {"intranet.acme.com", "loop.acme.com", "pods.acme.com", "metadata.acme.com", "ula.acme.com",
            "split.acme.com", "zero.acme.com", "unknown.acme.com"}) {
            assertThat(PublicHosts.isPublic(refused, resolver)).as(refused).isFalse();
        }
    }

    @Test
    void refusesAddressesAndPrivateNamesWithoutResolvingThem() {
        PublicHosts.Resolver everythingPublic = host -> new InetAddress[] {InetAddress.getByName("52.95.110.1")};

        for (String refused : new String[] {"52.95.110.1", "[::1]", "::1", "localhost", "indezy-server", "postgres.swirlit.internal",
            "printer.local", "kubernetes.default.svc", "api.default.svc.cluster.local", "box.home.arpa", "", null}) {
            assertThat(PublicHosts.isPublic(refused, everythingPublic)).as(String.valueOf(refused)).isFalse();
        }
    }
}
