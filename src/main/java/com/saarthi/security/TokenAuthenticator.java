package com.saarthi.security;

import com.saarthi.config.SecurityConfigValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves a device or operator token from request headers into a
 * {@link SaarthiPrincipal}. Authentication is deliberately non-mutating and
 * stateless: the same valid token always yields the same principal.
 */
@Component
public class TokenAuthenticator {

    public static final String DEVICE_HEADER = "X-Device-Token";
    public static final String OPERATOR_HEADER = "X-Operator-Token";

    private final SecurityConfigValidator validator;
    private final SecurityAuditLogger audit;

    public TokenAuthenticator(SecurityConfigValidator validator, SecurityAuditLogger audit) {
        this.validator = validator;
        this.audit = audit;
    }

    /**
     * @return the authenticated principal, or {@code null} when no valid token is presented.
     */
    public SaarthiPrincipal authenticate(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String uri = request.getRequestURI();
        String deviceToken = request.getHeader(DEVICE_HEADER);
        if (deviceToken != null && validator.matchesDeviceToken(deviceToken)) {
            audit.authSucceeded("DEVICE", remote, uri);
            return SaarthiPrincipal.device("device");
        }

        String operatorToken = request.getHeader(OPERATOR_HEADER);
        if (operatorToken != null && validator.matchesOperatorToken(operatorToken)) {
            audit.authSucceeded("OPERATOR", remote, uri);
            return SaarthiPrincipal.operator("operator");
        }

        // Only flag a failure when a credential header was actually presented but
        // was invalid. Plain anonymous traffic to public endpoints is expected and
        // is not an intrusion, so it is not logged to avoid audit log flooding.
        boolean presentedCredential = deviceToken != null || operatorToken != null;
        if (presentedCredential) {
            audit.authFailed("UNKNOWN", remote, uri, "invalid-token");
        }
        return null;
    }
}