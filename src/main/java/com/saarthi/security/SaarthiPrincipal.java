package com.saarthi.security;

import java.security.Principal;

/**
 * Represents the two trusted principals in the SAARTHI system:
 * a hardware device (ESP32/sensor node) or the manned operator dashboard.
 */
public record SaarthiPrincipal(String name, PrincipalType type) implements Principal {

    public enum PrincipalType {
        DEVICE,
        OPERATOR
    }

    public static SaarthiPrincipal device(String deviceName) {
        return new SaarthiPrincipal(deviceName, PrincipalType.DEVICE);
    }

    public static SaarthiPrincipal operator(String operatorName) {
        return new SaarthiPrincipal(operatorName, PrincipalType.OPERATOR);
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean isDevice() {
        return type == PrincipalType.DEVICE;
    }

    public boolean isOperator() {
        return type == PrincipalType.OPERATOR;
    }
}