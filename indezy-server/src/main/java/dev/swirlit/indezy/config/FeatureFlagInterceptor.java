package dev.swirlit.indezy.config;

import dev.swirlit.indezy.exception.FeatureDisabledException;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;

/** Refuses the API paths of a feature turned off, answering 403 with why. */
@Configuration
@RequiredArgsConstructor
public class FeatureFlagInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final FeatureFlagService flags;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Decoded and without ';' parameters, as Spring MVC matches it: /api/%6Fffers still reads /offers.
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        for (FeatureFlag flag : FeatureFlag.values()) {
            if (flag.covers(path) && !flags.isEnabled(flag)) {
                throw new FeatureDisabledException(flag);
            }
        }
        return true;
    }
}
