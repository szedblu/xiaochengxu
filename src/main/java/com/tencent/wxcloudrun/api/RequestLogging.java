package com.tencent.wxcloudrun.api;

import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RequestLogging implements WebMvcConfigurer {
    private static final Logger LOG = LoggerFactory.getLogger(RequestLogging.class);
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String requestId = request.getHeader("X-Cloudbase-Request-Id");
                if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{8,80}")) requestId = UUID.randomUUID().toString();
                MDC.put("requestId", requestId);
                request.setAttribute("logStartedAt", System.nanoTime());
                response.setHeader("X-Request-ID", requestId);
                response.setHeader("X-App-Request-ID", requestId);
                LOG.info("API request: method={}, path={}", request.getMethod(), request.getRequestURI());
                return true;
            }
            @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
                try {
                    long elapsed = (System.nanoTime() - (Long) request.getAttribute("logStartedAt")) / 1000000;
                    LOG.info("API response: method={}, path={}, status={}, durationMs={}", request.getMethod(), request.getRequestURI(), response.getStatus(), elapsed);
                    if (exception != null) LOG.error("API dispatch failed", SafeLogs.exception(exception));
                } finally { MDC.remove("requestId"); }
            }
        }).addPathPatterns("/api/**");
    }
}
