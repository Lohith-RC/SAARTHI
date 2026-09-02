package com.saarthi.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bucket4j sliding-window rate limiting filter (resolves P0-SEC-002).
 * Rate limits AI endpoints:
 * - 10 req/min for authenticated operators
 * - 3 req/min for unauthenticated / IP
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${saarthi.ratelimit.operator-rpm:10}")
    private int operatorRpm;

    @Value("${saarthi.ratelimit.anonymous-rpm:3}")
    private int anonymousRpm;

    @Value("${saarthi.ratelimit.enabled:true}")
    private boolean rateLimitEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        // Only rate limit heavy AI endpoints
        if (uri.startsWith("/api/v1/ai/")) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String key;
            int capacity;

            if (auth != null && auth.getPrincipal() instanceof SaarthiPrincipal sp && sp.isOperator()) {
                key = "operator:" + sp.getName();
                capacity = operatorRpm;
            } else {
                key = "ip:" + request.getRemoteAddr();
                capacity = anonymousRpm;
            }

            Bucket bucket = buckets.computeIfAbsent(key, k -> createNewBucket(capacity));

            if (!bucket.tryConsume(1)) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.setHeader("Retry-After", "60");
                response.getWriter().write("{\"error\":{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded. Please wait before retrying.\"}}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private Bucket createNewBucket(int capacity) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
