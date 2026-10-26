package dev.swirlit.indezy.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void keepsAPlainIdTheCallerGaveAndLogsItWithTheMethodDuringTheRequestOnly() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects");
        request.addHeader("X-Request-Id", "trace-42.a_b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> seen = new ArrayList<>();

        filter.doFilter(request, response, (req, res) -> {
            seen.add(MDC.get("request_id"));
            seen.add(MDC.get("method"));
        });

        assertThat(seen).containsExactly("trace-42.a_b", "POST");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("trace-42.a_b");
        assertThat(MDC.get("request_id")).isNull();
        assertThat(MDC.get("method")).isNull();
    }

    @Test
    void givesANewIdWhenThereIsNoneOrItIsNotPlain() throws Exception {
        for (String given : new String[] {null, "", "evil\nline", "x".repeat(65)}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/offers");
            if (given != null) {
                request.addHeader("X-Request-Id", given);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> { });

            assertThat(response.getHeader("X-Request-Id")).matches("[0-9a-f-]{36}");
        }
    }

    @Test
    void forgetsTheIdWhenTheRequestFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/offers");

        try {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                throw new IllegalStateException("boom");
            });
        } catch (Exception expected) {
            assertThat(expected).hasMessageContaining("boom");
        }

        assertThat(MDC.get("request_id")).isNull();
    }
}
