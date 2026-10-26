package dev.swirlit.indezy.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request an id, the caller's {@code X-Request-Id} when it is a plain one, else a new one, sent back in the
 * response and written with the request's method on each log line it causes ({@code request_id}, {@code method} in the
 * structured logs). The path stays out: some carry a token (an unsubscribe link).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";
    static final String REQUEST_ID = "request_id";
    static final String METHOD = "method";
    private static final Pattern PLAIN_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        String id = given != null && PLAIN_ID.matcher(given).matches() ? given : UUID.randomUUID().toString();
        response.setHeader(HEADER, id);
        MDC.put(REQUEST_ID, id);
        MDC.put(METHOD, request.getMethod());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID);
            MDC.remove(METHOD);
        }
    }
}
