package dev.swirlit.indezy.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookAddressesTest {

    private final WebhookAddresses addresses = new WebhookAddresses(false);

    @Test
    void acceptsHttpsToPublicAddressesOnly() {
        assertThat(addresses.requireValid("https://93.184.216.34/hooks/indezy?key=1")).hasToString("https://93.184.216.34/hooks/indezy?key=1");
        for (String refused : new String[] {"http://93.184.216.34/hook", "https://user:pass@93.184.216.34/hook", "https://93.184.216.34/#x",
            "https:///nohost", "not a url", "https://127.0.0.1/hook", "https://10.0.0.5/hook", "https://192.168.1.1/hook",
            "https://172.16.0.1/hook", "https://169.254.169.254/latest/meta-data", "https://100.64.0.1/hook", "https://0.0.0.1/hook",
            "https://[::1]/hook", "https://[fd00::1]/hook", "https://[fe80::1]/hook", "https://224.0.0.1/hook", "https://0.0.0.0/hook",
            "https://does-not-resolve.invalid/hook"}) {
            assertThatThrownBy(() -> addresses.requireValid(refused)).as(refused).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void judgesAddresses() throws Exception {
        assertThat(WebhookAddresses.isPublic(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
        assertThat(WebhookAddresses.isPublic(InetAddress.getByName("100.128.0.1"))).isTrue();
        assertThat(WebhookAddresses.isPublic(InetAddress.getByName("100.63.255.255"))).isTrue();
        assertThat(WebhookAddresses.isPublic(InetAddress.getByName("::ffff:10.0.0.1"))).isFalse();
    }

    @Test
    void letsTestsPostToALocalReceiver() {
        WebhookAddresses local = new WebhookAddresses(true);
        assertThat(local.requireValid("http://127.0.0.1:8080/hook")).hasToString("http://127.0.0.1:8080/hook");
        local.requirePublic("localhost");
    }

    @Test
    void signsWithTheSecret() {
        // RFC 4231 test case 2.
        assertThat(WebhookDeliveryJob.sign("Jefe", "what do ya want for nothing?"))
            .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }
}
