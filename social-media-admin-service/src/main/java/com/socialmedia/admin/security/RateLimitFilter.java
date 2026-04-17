package com.socialmedia.admin.security;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * In-memory rate limiter for admin endpoints.
 * Limits requests per user (X-User-Id header) within a sliding window.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${admin.rate-limit.max-requests:100}")
    private int maxRequests;

    @Value("${admin.rate-limit.window-seconds:60}")
    private int windowSeconds;

    private final Map<String, RateBucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        if (userId == null || !request.getRequestURI().startsWith("/api/admin")) {
            chain.doFilter(request, response);
            return;
        }

        RateBucket bucket = buckets.computeIfAbsent(userId, k -> new RateBucket());
        long now = System.currentTimeMillis();

        synchronized (bucket) {
            if (now - bucket.windowStart > windowSeconds * 1000L) {
                bucket.windowStart = now;
                bucket.count.set(0);
            }

            if (bucket.count.incrementAndGet() > maxRequests) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests\",\"message\":\"Rate limit exceeded. Max " + maxRequests + " requests per " + windowSeconds + " seconds.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private static class RateBucket {
        long windowStart = System.currentTimeMillis();
        final AtomicInteger count = new AtomicInteger(0);
    }
}
