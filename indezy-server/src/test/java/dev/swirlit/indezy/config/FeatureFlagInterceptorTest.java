package dev.swirlit.indezy.config;

import dev.swirlit.indezy.exception.FeatureDisabledException;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureFlagInterceptorTest {

    private final FeatureFlagService flags = mock(FeatureFlagService.class);
    private final FeatureFlagInterceptor interceptor = new FeatureFlagInterceptor(flags);

    private boolean handles(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath("/api");
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void refusesEveryWayOfWritingThePathOfAFeatureTurnedOff() {
        when(flags.isEnabled(FeatureFlag.OFFER_FEED)).thenReturn(false);
        when(flags.isEnabled(FeatureFlag.PUBLIC_PROFILES)).thenReturn(true);
        when(flags.isEnabled(FeatureFlag.DATA_IMPORT)).thenReturn(true);
        when(flags.isEnabled(FeatureFlag.YEARLY_REVIEW)).thenReturn(true);

        for (String uri : new String[] {"/api/offers", "/api/offers/12", "/api/%6Fffers/12", "/api/offers;x=1/12"}) {
            assertThatThrownBy(() -> handles(uri)).as(uri).isInstanceOf(FeatureDisabledException.class)
                .extracting(error -> ((FeatureDisabledException) error).getFlag()).isEqualTo(FeatureFlag.OFFER_FEED);
        }
        assertThat(handles("/api/offersx")).isTrue();
        assertThat(handles("/api/users/profile")).isTrue();
        assertThat(handles("/api/users/public-profile")).isTrue();
    }
}
