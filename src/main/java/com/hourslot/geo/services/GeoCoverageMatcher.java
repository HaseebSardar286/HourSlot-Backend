package com.hourslot.geo.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Pure coverage rules for home-service matching. Does not fabricate providers.
 */
public final class GeoCoverageMatcher {
    private static final double EARTH_KM = 6371.0;

    public record CoverageArea(
            String coverageType,
            Long geoAreaId,
            String areaName,
            String city,
            Double latitude,
            Double longitude,
            BigDecimal radiusKm
    ) {}

    public record BranchLocation(
            String city,
            Double latitude,
            Double longitude
    ) {}

    public record RequestLocation(
            Long geoAreaId,
            String city,
            String areaName,
            Double latitude,
            Double longitude
    ) {}

    public record MatchDecision(
            boolean eligible,
            BigDecimal score,
            String reason,
            Double distanceKm
    ) {}

    private GeoCoverageMatcher() {}

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static MatchDecision evaluate(
            String serviceMode,
            boolean verified,
            Double ratingAvg,
            List<CoverageArea> areas,
            List<BranchLocation> branches,
            RequestLocation location) {
        String mode = serviceMode == null ? "AT_PROVIDER" : serviceMode.trim().toUpperCase(Locale.ROOT);
        if ("REMOTE".equals(mode)) {
            return scored(true, 40, "remote", null, verified, ratingAvg);
        }

        CoverageHit coverage = bestCoverageHit(areas, location);
        boolean branchCity = branchCityMatches(branches, location);
        boolean nearShop = shopWithinRadius(branches, location, 15.0);

        boolean eligible;
        String reason;
        Double distance = coverage.distanceKm;
        int coverageScore;

        if ("CUSTOMER_LOCATION".equals(mode) || "BOTH".equals(mode)) {
            eligible = coverage.hit;
            reason = coverage.hit ? coverage.reason : "outside_service_area";
            coverageScore = coverage.score;
        } else if ("HYBRID".equals(mode)) {
            eligible = coverage.hit || branchCity || nearShop;
            if (coverage.hit) {
                reason = coverage.reason;
                coverageScore = coverage.score;
            } else if (branchCity) {
                reason = "branch_city";
                coverageScore = 35;
            } else if (nearShop) {
                reason = "near_branch";
                coverageScore = 30;
                distance = nearestShopKm(branches, location);
            } else {
                reason = "outside_service_area";
                coverageScore = 0;
            }
        } else {
            eligible = branchCity || nearShop;
            if (branchCity) {
                reason = "branch_city";
                coverageScore = 40;
            } else if (nearShop) {
                reason = "near_branch";
                coverageScore = 28;
                distance = nearestShopKm(branches, location);
            } else {
                reason = "outside_city";
                coverageScore = 0;
            }
        }

        return scored(eligible, coverageScore, reason, distance, verified, ratingAvg);
    }

    private static MatchDecision scored(
            boolean eligible,
            int coverageScore,
            String reason,
            Double distanceKm,
            boolean verified,
            Double ratingAvg) {
        if (!eligible) {
            return new MatchDecision(false, BigDecimal.ZERO, reason, distanceKm);
        }
        double score = coverageScore;
        if (verified) {
            score += 10;
        }
        if (ratingAvg != null && ratingAvg > 0) {
            score += Math.min(20, ratingAvg * 4);
        }
        return new MatchDecision(
                true,
                BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP),
                reason,
                distanceKm);
    }

    private static CoverageHit bestCoverageHit(List<CoverageArea> areas, RequestLocation location) {
        CoverageHit best = CoverageHit.MISS;
        if (areas == null) {
            return best;
        }
        for (CoverageArea area : areas) {
            if (area == null) {
                continue;
            }
            String type = area.coverageType() == null ? "" : area.coverageType().toUpperCase(Locale.ROOT);
            if ("NAMED".equals(type)) {
                if (namedMatch(area, location)) {
                    int score = 55;
                    if (location.geoAreaId() != null && location.geoAreaId().equals(area.geoAreaId())) {
                        score = 70;
                    }
                    if (score > best.score) {
                        best = new CoverageHit(true, score, "named_area", null);
                    }
                }
            } else if ("RADIUS".equals(type)
                    && area.latitude() != null && area.longitude() != null
                    && location.latitude() != null && location.longitude() != null
                    && area.radiusKm() != null && area.radiusKm().signum() > 0) {
                double km = haversineKm(
                        location.latitude(), location.longitude(),
                        area.latitude(), area.longitude());
                if (km <= area.radiusKm().doubleValue()) {
                    double closeness = 1.0 - (km / area.radiusKm().doubleValue());
                    int score = 40 + (int) Math.round(closeness * 25);
                    if (score > best.score) {
                        best = new CoverageHit(true, score, "radius", km);
                    }
                }
            }
        }
        return best;
    }

    private static boolean namedMatch(CoverageArea area, RequestLocation location) {
        if (location.geoAreaId() != null && location.geoAreaId().equals(area.geoAreaId())) {
            return true;
        }
        if (eqIgnore(area.city(), location.city()) && eqIgnore(area.areaName(), location.areaName())) {
            return true;
        }
        return eqIgnore(area.city(), location.city())
                && location.areaName() != null
                && area.areaName() != null
                && location.areaName().toLowerCase(Locale.ROOT).contains(area.areaName().toLowerCase(Locale.ROOT));
    }

    private static boolean branchCityMatches(List<BranchLocation> branches, RequestLocation location) {
        if (branches == null || location.city() == null || location.city().isBlank()) {
            return false;
        }
        for (BranchLocation branch : branches) {
            if (branch != null && eqIgnore(branch.city(), location.city())) {
                return true;
            }
        }
        return false;
    }

    private static boolean shopWithinRadius(List<BranchLocation> branches, RequestLocation location, double maxKm) {
        Double km = nearestShopKm(branches, location);
        return km != null && km <= maxKm;
    }

    private static Double nearestShopKm(List<BranchLocation> branches, RequestLocation location) {
        if (branches == null || location.latitude() == null || location.longitude() == null) {
            return null;
        }
        Double best = null;
        for (BranchLocation branch : branches) {
            if (branch == null || branch.latitude() == null || branch.longitude() == null) {
                continue;
            }
            double km = haversineKm(
                    location.latitude(), location.longitude(),
                    branch.latitude(), branch.longitude());
            if (best == null || km < best) {
                best = km;
            }
        }
        return best;
    }

    private static boolean eqIgnore(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private record CoverageHit(boolean hit, int score, String reason, Double distanceKm) {
        static final CoverageHit MISS = new CoverageHit(false, 0, "none", null);
    }
}
