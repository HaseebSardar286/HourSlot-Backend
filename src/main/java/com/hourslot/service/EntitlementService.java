package com.hourslot.service;

import com.hourslot.model.Organization;
import com.hourslot.model.OrganizationSubscription;
import com.hourslot.model.PlanEntitlement;
import com.hourslot.model.SubscriptionPlan;
import com.hourslot.repository.BranchRepository;
import com.hourslot.repository.BusinessRepository;
import com.hourslot.repository.OrganizationSubscriptionRepository;
import com.hourslot.repository.PlanEntitlementRepository;
import com.hourslot.repository.StaffRepository;
import com.hourslot.repository.SubscriptionPlanRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EntitlementService {

    public static final String MAX_BRANCHES = "max_branches";
    public static final String MAX_STAFF = "max_staff";
    public static final String MAX_BUSINESSES = "max_businesses";
    public static final String PEAK_PRICING = "peak_pricing";
    public static final String PACKAGES = "packages";
    public static final String WAITLIST = "waitlist";
    public static final String LAST_MINUTE_DEALS = "last_minute_deals";
    public static final String SMS_MONTHLY = "sms_monthly";
    public static final String YIELD_DASHBOARD = "yield_dashboard";
    public static final String WHITE_LABEL = "white_label";
    public static final String OWNER_REPLY = "owner_reply";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OwnerPlanSnapshot {
        private String planCode;
        private String planName;
        private String status;
        private BigDecimal price;
        private String currency;
        private String billingInterval;
        private Map<String, Object> entitlements;
        private Map<String, Long> usage;
        private Map<String, String> unlocksAt;
    }

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PlanEntitlementRepository planEntitlementRepository;
    private final OrganizationSubscriptionRepository organizationSubscriptionRepository;
    private final BranchRepository branchRepository;
    private final StaffRepository staffRepository;
    private final BusinessRepository businessRepository;

    public EntitlementService(
            SubscriptionPlanRepository subscriptionPlanRepository,
            PlanEntitlementRepository planEntitlementRepository,
            OrganizationSubscriptionRepository organizationSubscriptionRepository,
            BranchRepository branchRepository,
            StaffRepository staffRepository,
            BusinessRepository businessRepository) {
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.planEntitlementRepository = planEntitlementRepository;
        this.organizationSubscriptionRepository = organizationSubscriptionRepository;
        this.branchRepository = branchRepository;
        this.staffRepository = staffRepository;
        this.businessRepository = businessRepository;
    }

    @Transactional
    public OrganizationSubscription ensureStarter(Organization organization) {
        return organizationSubscriptionRepository.findActive(organization)
                .orElseGet(() -> {
                    SubscriptionPlan starter = subscriptionPlanRepository.findByCode("STARTER")
                            .orElseThrow(() -> new IllegalStateException("STARTER plan is not seeded"));
                    OrganizationSubscription created = OrganizationSubscription.builder()
                            .organization(organization)
                            .plan(starter)
                            .status("ACTIVE")
                            .cancelAtPeriodEnd(false)
                            .build();
                    return organizationSubscriptionRepository.save(created);
                });
    }

    @Transactional
    public OwnerPlanSnapshot snapshot(Organization organization) {
        OrganizationSubscription subscription = ensureStarter(organization);
        SubscriptionPlan plan = subscription.getPlan();
        Map<String, Object> entitlements = entitlementsOf(plan, subscription.getEntitlementOverrides());
        Map<String, Long> usage = new LinkedHashMap<>();
        usage.put("branches", countBranches(organization));
        usage.put("staff", countStaff(organization));
        usage.put("businesses", countBusinesses(organization));

        Map<String, String> unlocksAt = new LinkedHashMap<>();
        for (String code : List.of(
                PEAK_PRICING, PACKAGES, WAITLIST, LAST_MINUTE_DEALS, YIELD_DASHBOARD, WHITE_LABEL, OWNER_REPLY)) {
            if (!asBoolean(entitlements.get(code))) {
                unlocksAt.put(code, firstPlanUnlocking(code));
            }
        }
        unlocksAt.put(MAX_BRANCHES, firstPlanWithHigherLimit(MAX_BRANCHES, asInt(entitlements.get(MAX_BRANCHES), 1)));
        unlocksAt.put(MAX_STAFF, firstPlanWithHigherLimit(MAX_STAFF, asInt(entitlements.get(MAX_STAFF), 2)));

        return new OwnerPlanSnapshot(
                plan.getCode(),
                plan.getName(),
                subscription.getStatus(),
                plan.getPrice(),
                plan.getCurrency(),
                plan.getBillingInterval(),
                entitlements,
                usage,
                unlocksAt
        );
    }

    @Transactional
    public Map<String, Object> resolve(Organization organization) {
        return resolve(organization, true);
    }

    @Transactional(readOnly = true)
    public boolean allows(Organization organization, String entitlementCode) {
        if (organization == null) {
            return false;
        }
        return asBoolean(resolve(organization, false).get(entitlementCode));
    }

    @Transactional
    public void requireFeature(Organization organization, String entitlementCode, String featureName) {
        Map<String, Object> values = resolve(organization, true);
        if (asBoolean(values.get(entitlementCode))) {
            return;
        }
        String current = ensureStarter(organization).getPlan().getName();
        String target = firstPlanUnlocking(entitlementCode);
        throw new PlanLimitException(
                entitlementCode,
                "Your " + current + " plan does not include " + featureName
                        + ". Upgrade to " + target + " to unlock it."
        );
    }

    @Transactional
    public void requireHeadroom(Organization organization, String limitCode, long currentCount, String unit) {
        Map<String, Object> values = resolve(organization, true);
        int max = asInt(values.get(limitCode), 0);
        if (max >= 999 || currentCount < max) {
            return;
        }
        String current = ensureStarter(organization).getPlan().getName();
        String target = firstPlanWithHigherLimit(limitCode, max);
        throw new PlanLimitException(
                limitCode,
                "Your " + current + " plan allows " + max + " " + unit
                        + ". Upgrade to " + target + " to add more."
        );
    }

    public long countBranches(Organization organization) {
        return branchRepository.countByOrganizationId(organization.getId());
    }

    public long countStaff(Organization organization) {
        return staffRepository.countByOrganizationId(organization.getId());
    }

    public long countBusinesses(Organization organization) {
        return businessRepository.countByOrganizationId(organization.getId());
    }

    private Map<String, Object> resolve(Organization organization, boolean persistStarter) {
        return organizationSubscriptionRepository.findActive(organization)
                .map(subscription -> entitlementsOf(subscription.getPlan(), subscription.getEntitlementOverrides()))
                .orElseGet(() -> {
                    if (persistStarter) {
                        OrganizationSubscription created = ensureStarter(organization);
                        return entitlementsOf(created.getPlan(), created.getEntitlementOverrides());
                    }
                    SubscriptionPlan starter = subscriptionPlanRepository.findByCodeWithEntitlements("STARTER")
                            .orElseThrow(() -> new IllegalStateException("STARTER plan is not seeded"));
                    return entitlementsOf(starter, null);
                });
    }

    private Map<String, Object> entitlementsOf(SubscriptionPlan plan, Map<String, Object> overrides) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<PlanEntitlement> rows = plan.getEntitlements();
        if (rows == null || !org.hibernate.Hibernate.isInitialized(rows) || rows.isEmpty()) {
            rows = planEntitlementRepository.findByPlan(plan);
        }
        for (PlanEntitlement row : rows) {
            values.put(row.getEntitlementCode(), coerce(row.getValueType(), row.getValue()));
        }
        if (overrides != null) {
            values.putAll(overrides);
        }
        return values;
    }

    private String firstPlanUnlocking(String entitlementCode) {
        for (SubscriptionPlan plan : subscriptionPlanRepository.findByActiveTrueOrderBySortOrderAsc()) {
            for (PlanEntitlement row : planEntitlementRepository.findByPlan(plan)) {
                if (entitlementCode.equals(row.getEntitlementCode())
                        && asBoolean(coerce(row.getValueType(), row.getValue()))) {
                    return plan.getName();
                }
            }
        }
        return "a paid plan";
    }

    private String firstPlanWithHigherLimit(String limitCode, int currentMax) {
        for (SubscriptionPlan plan : subscriptionPlanRepository.findByActiveTrueOrderBySortOrderAsc()) {
            for (PlanEntitlement row : planEntitlementRepository.findByPlan(plan)) {
                if (limitCode.equals(row.getEntitlementCode())
                        && asInt(coerce(row.getValueType(), row.getValue()), 0) > currentMax) {
                    return plan.getName();
                }
            }
        }
        return "a higher plan";
    }

    private static Object coerce(String valueType, String raw) {
        if (raw == null) {
            return null;
        }
        String type = valueType == null ? "STRING" : valueType.toUpperCase(Locale.ROOT);
        return switch (type) {
            case "BOOL", "BOOLEAN" -> Boolean.parseBoolean(raw);
            case "INT", "INTEGER" -> {
                try {
                    yield Integer.parseInt(raw.trim());
                } catch (NumberFormatException e) {
                    yield 0;
                }
            }
            default -> raw;
        };
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
