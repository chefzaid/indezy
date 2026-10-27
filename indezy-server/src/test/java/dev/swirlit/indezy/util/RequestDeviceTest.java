package dev.swirlit.indezy.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class RequestDeviceTest {

    @Test
    void namesTheBrowserAndSystemOfCommonUserAgents() {
        assertThat(RequestDevice.describe("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")).isEqualTo("Edge on Windows");
        assertThat(RequestDevice.describe("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
            "Version/18.0 Safari/605.1.15")).isEqualTo("Safari on macOS");
        assertThat(RequestDevice.describe("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1")).isEqualTo("Safari on iPhone");
        assertThat(RequestDevice.describe("Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/130.0.0.0 Mobile Safari/537.36")).isEqualTo("Chrome on Android");
        assertThat(RequestDevice.describe("Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0"))
            .isEqualTo("Firefox on Linux");
        assertThat(RequestDevice.describe("curl/8.10.1")).isEqualTo("curl");
        assertThat(RequestDevice.describe("Java-http-client/25")).isEqualTo("Other");
        assertThat(RequestDevice.describe(" ")).isNull();
        assertThat(RequestDevice.describe(null)).isNull();
    }

    @Test
    void readsTheRequestBeingServed() {
        assertThat(RequestDevice.current()).isNull();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertThat(RequestDevice.current()).isEqualTo("Chrome on ChromeOS");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
