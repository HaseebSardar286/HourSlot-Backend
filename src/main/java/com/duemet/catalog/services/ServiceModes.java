package com.duemet.catalog.services;

import com.duemet.catalog.model.Service;

/** Where a service is delivered. Shared by slot listing and booking validation so both agree. */
public final class ServiceModes {

    private ServiceModes() {
    }

    /** The provider can come to the customer (always, or as an option). */
    public static boolean supportsHomeVisit(Service service) {
        if (service == null) {
            return false;
        }
        String mode = service.getServiceMode();
        return service.isAllowsHomeService()
                || "CUSTOMER_LOCATION".equalsIgnoreCase(mode)
                || "HYBRID".equalsIgnoreCase(mode)
                || "BOTH".equalsIgnoreCase(mode);
    }

    /** The service only happens at the customer's location, so a visit address is mandatory. */
    public static boolean requiresHomeVisit(Service service) {
        return service != null && "CUSTOMER_LOCATION".equalsIgnoreCase(service.getServiceMode());
    }
}
