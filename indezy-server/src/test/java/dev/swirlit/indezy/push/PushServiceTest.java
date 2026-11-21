package dev.swirlit.indezy.push;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushServiceTest {

    @Test
    void reachesOnlyThePushServicesOfKnownBrowsers() {
        assertThat(PushService.allowed("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(PushService.allowed("https://updates.push.services.mozilla.com/wpush/v2/abc")).isTrue();
        assertThat(PushService.allowed("https://web.push.apple.com/QGuQ")).isTrue();
        assertThat(PushService.allowed("https://wns2-par02p.notify.windows.com/w/?token=abc")).isTrue();
        assertThat(PushService.allowed("http://fcm.googleapis.com/fcm/send/abc")).isFalse();
        assertThat(PushService.allowed("https://fcm.googleapis.com:8443/fcm/send/abc")).isFalse();
        assertThat(PushService.allowed("https://evil.example/fcm.googleapis.com")).isFalse();
        assertThat(PushService.allowed("https://fcm.googleapis.com.evil.example/x")).isFalse();
        assertThat(PushService.allowed("https://user@fcm.googleapis.com/x")).isFalse();
        assertThat(PushService.allowed("https://169.254.169.254/latest")).isFalse();
        assertThat(PushService.allowed("not a url at all")).isFalse();
    }

    @Test
    void saysPushIsOffWithoutBothKeys() {
        assertThat(new PushProperties(null, null).configured()).isFalse();
        assertThat(new PushProperties("public", " ").configured()).isFalse();
        assertThat(new PushProperties("public", "private").configured()).isTrue();
        assertThat(new PushProperties("public", "private").toString()).doesNotContain("private");
    }
}
