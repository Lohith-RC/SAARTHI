package com.saarthi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup security configuration validator.
 * Refuses to boot when device/operator tokens are missing, blank, or still set
 * to the well-known deprecated public default. Runs on ApplicationReadyEvent.
 */
@Component
public class SecurityConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfigValidator.class);

    private static final String DEPRECATED_PUBLIC_TOKEN = "SAARTHI_UUID4_MASTER_SECRET";

    private final String deviceToken;
    private final String operatorToken;

    public SecurityConfigValidator(
            @Value("${saarthi.security.device-token:}") String deviceToken,
            @Value("${saarthi.security.operator-token:}") String operatorToken) {
        this.deviceToken = deviceToken;
        this.operatorToken = operatorToken;
    }

    /**
     * Deterministic, constant-time comparison against a known secret.
     */
    public boolean matchesDeviceToken(String candidate) {
        return TokenDecoding.constantTimeEquals(deviceToken, candidate);
    }

    public boolean matchesOperatorToken(String candidate) {
        return TokenDecoding.constantTimeEquals(operatorToken, candidate);
    }

    public String operatorToken() {
        return operatorToken;
    }

    public void validate() {
        boolean invalid = false;

        if (deviceToken == null || deviceToken.isBlank()) {
            log.error("[SECURITY] SAARTHI_DEVICE_TOKEN is not set. Refusing to start.");
            invalid = true;
        } else if (DEPRECATED_PUBLIC_TOKEN.equals(deviceToken.trim())) {
            log.error("[SECURITY] SAARTHI_DEVICE_TOKEN is still the public default. Rotate it immediately.");
            invalid = true;
        }

        if (operatorToken == null || operatorToken.isBlank()) {
            log.error("[SECURITY] SAARTHI_OPERATOR_TOKEN is not set. Refusing to start.");
            invalid = true;
        } else if (DEPRECATED_PUBLIC_TOKEN.equals(operatorToken.trim())) {
            log.error("[SECURITY] SAARTHI_OPERATOR_TOKEN is still the public default. Rotate it immediately.");
            invalid = true;
        }

        if (invalid) {
            throw new IllegalStateException(
                    "Security configuration is invalid. Both SAARTHI_DEVICE_TOKEN and SAARTHI_OPERATOR_TOKEN " +
                    "must be set to unique, non-default secrets before the application can start.");
        }
    }
}