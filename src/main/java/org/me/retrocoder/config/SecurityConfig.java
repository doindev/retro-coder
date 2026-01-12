package org.me.retrocoder.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Set;

/**
 * Security configuration for localhost-only access.
 */
@Slf4j
@Configuration
public class SecurityConfig {

    @Value("${autocoder.security.localhost-only:true}")
    private boolean localhostOnly;

    private static final Set<String> ALLOWED_IPS = Set.of(
        "127.0.0.1",
        "0:0:0:0:0:0:0:1",
        "::1"
    );

    @Bean
    public FilterRegistrationBean<LocalhostFilter> localhostFilter() {
        FilterRegistrationBean<LocalhostFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new LocalhostFilter(localhostOnly));
        // Only apply to API endpoints, not WebSocket (WS handles its own security)
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("localhostFilter");
        return registration;
    }

    public static class LocalhostFilter implements Filter {
        private final boolean enabled;

        public LocalhostFilter(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            if (!enabled) {
                chain.doFilter(request, response);
                return;
            }

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            String remoteAddr = httpRequest.getRemoteAddr();

            if (!ALLOWED_IPS.contains(remoteAddr)) {
                log.warn("Blocked request from non-localhost IP: {}", remoteAddr);
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                httpResponse.getWriter().write("Access denied: localhost only");
                return;
            }

            chain.doFilter(request, response);
        }
    }
}
