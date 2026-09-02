package com.saarthi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stateless bearer-token filter for the two SAARTHI principals. Reads
 * {@code X-Device-Token} or {@code X-Operator-Token} and, when valid, installs
 * the corresponding principal into the {@link SecurityContext}. Requests without
 * a valid token remain anonymous; authorization is enforced by the
 * {@code SecurityFilterChain} route rules.
 */
@Component
public class SaarthiAuthenticationFilter extends OncePerRequestFilter {

    private final TokenAuthenticator tokenAuthenticator;

    public SaarthiAuthenticationFilter(TokenAuthenticator tokenAuthenticator) {
        this.tokenAuthenticator = tokenAuthenticator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        SaarthiPrincipal principal = tokenAuthenticator.authenticate(request);

        if (principal != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, null);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}