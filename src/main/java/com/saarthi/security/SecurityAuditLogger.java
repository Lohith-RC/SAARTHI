package com.saarthi.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Central audit trail for security-sensitive events.
 *
 * Deliberately logs only safe descriptors (principal type, remote address,
 * action, outcome) and NEVER the token/API-key values themselves, so raw
 * authentication material cannot leak into logs.
 */
@Component
public class SecurityAuditLogger {

    private static final Logger audit = LoggerFactory.getLogger("SECURITY_AUDIT");

    public void authSucceeded(String principalType, String remoteAddr, String action) {
        audit.info("AUTH_OK principal={} remote={} action={}", safe(principalType), safe(remoteAddr), safe(action));
    }

    public void authFailed(String principalType, String remoteAddr, String action, String reason) {
        audit.warn("AUTH_DENIED principal={} remote={} action={} reason={}",
                safe(principalType), safe(remoteAddr), safe(action), safe(reason));
    }

    public void actuatorIssued(String target, String remoteAddr) {
        audit.info("ACTUATION target={} remote={}", safe(target), safe(remoteAddr));
    }

    public void alertDispatched(String level, String title, String remoteAddr) {
        audit.info("ALERT_DISPATCH level={} title={} remote={}", safe(level), safe(title), safe(remoteAddr));
    }

    public void aiActuationSuppressed(String remoteAddr) {
        audit.warn("AI_ACTUATION_SUPPRESSED remote={}", safe(remoteAddr));
    }

    private static String safe(Object value) {
        if (value == null) {
            return "null";
        }
        String s = value.toString();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}