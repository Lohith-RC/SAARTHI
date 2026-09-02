package com.saarthi.security;

import com.saarthi.security.SaarthiPrincipal.PrincipalType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/**
 * Grants access when the authenticated principal is of the required type.
 * Anonymous requests and principals of the wrong type are denied. Expected to
 * be matched after an {@code authenticated()} rule on the same routes.
 */
public class PrincipalTypeAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final PrincipalType requiredType;

    public PrincipalTypeAuthorizationManager(PrincipalType requiredType) {
        this.requiredType = requiredType;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext object) {
        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof SaarthiPrincipal principal)) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(principal.type() == requiredType);
    }
}