package com.hourslot.service;

public class PlanLimitException extends RuntimeException {

    private final String entitlementCode;

    public PlanLimitException(String entitlementCode, String message) {
        super(message);
        this.entitlementCode = entitlementCode;
    }

    public String getEntitlementCode() {
        return entitlementCode;
    }
}
