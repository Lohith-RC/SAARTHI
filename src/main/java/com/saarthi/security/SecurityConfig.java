package com.saarthi.security;

import com.saarthi.security.SaarthiPrincipal.PrincipalType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Central Spring Security configuration for SAARTHI.
 *
 * <p>Authentication is stateless and header-based: {@code X-Device-Token}
 * authenticates a hardware device, {@code X-Operator-Token} authenticates the
 * operator dashboard. The HUD page and public read endpoints stay anonymous;
 * everything else requires a specific principal type.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SaarthiAuthenticationFilter authenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;

    public SecurityConfig(SaarthiAuthenticationFilter authenticationFilter, RateLimitingFilter rateLimitingFilter) {
        this.authenticationFilter = authenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public HUD + live read endpoints + static assets
                .requestMatchers(
                        "/", "/index.html", "/index", "/hud", "/lite.html", "/lite",
                        "/style.css", "/app.js", "/lite.css", "/lite.js",
                        "/*.css", "/*.js", "/*.html", "/*.png", "/*.jpg", "/*.jpeg", "/*.svg", "/*.ico", "/*.woff2", "/*.woff", "/*.ttf",
                        "/favicon.ico", "/error",
                        "/css/**", "/js/**", "/assets/**", "/images/**", "/fonts/**", "/static/**"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/telemetry/current").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/ai/status").permitAll()
                .requestMatchers("/ws/telemetry").permitAll()

                // Operator-only AI endpoints (chat, TTS, memory, image generation)
                .requestMatchers("/api/v1/ai/chat", "/api/v1/ai/tts", "/api/v1/ai/generate-crop-image",
                        "/api/v1/ai/memory/**")
                    .access(new PrincipalTypeAuthorizationManager(PrincipalType.OPERATOR))

                // Device-only ingestion
                .requestMatchers(HttpMethod.POST, "/api/v1/telemetry/push")
                    .access(new PrincipalTypeAuthorizationManager(PrincipalType.DEVICE))

                // Operator-only management & AI endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/actuate", "/api/v1/simulate/**")
                    .access(new PrincipalTypeAuthorizationManager(PrincipalType.OPERATOR))
                .requestMatchers("/api/v1/alerts/**", "/actuator/**")
                    .access(new PrincipalTypeAuthorizationManager(PrincipalType.OPERATOR))

                // Anything unlisted is denied by default (fail-closed)
                .anyRequest().denyAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Unauthorized\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Forbidden\"}");
                })
            )
            .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitingFilter, SaarthiAuthenticationFilter.class);

        return http.build();
    }
}