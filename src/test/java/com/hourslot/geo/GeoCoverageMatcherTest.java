package com.hourslot.geo;

import com.hourslot.geo.services.GeoCoverageMatcher;
import com.hourslot.geo.services.GeoCoverageMatcher.BranchLocation;
import com.hourslot.geo.services.GeoCoverageMatcher.CoverageArea;
import com.hourslot.geo.services.GeoCoverageMatcher.MatchDecision;
import com.hourslot.geo.services.GeoCoverageMatcher.RequestLocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeoCoverageMatcherTest {

    private static final RequestLocation DHA = new RequestLocation(
            11L, "Lahore", "Demo DHA Phase 5", 31.4697, 74.4105);

    @Test
    void namedAreaMatchesGeoAreaId() {
        CoverageArea area = new CoverageArea("NAMED", 11L, "Demo DHA Phase 5", "Lahore", null, null, null);
        MatchDecision decision = GeoCoverageMatcher.evaluate(
                "CUSTOMER_LOCATION", true, 4.5, List.of(area), List.of(), DHA);
        assertTrue(decision.eligible());
        assertTrue(decision.score().doubleValue() >= 70);
        assertEquals("named_area", decision.reason());
    }

    @Test
    void radiusRejectsOutsideCoverage() {
        CoverageArea area = new CoverageArea(
                "RADIUS", null, "Gulberg", "Lahore", 31.5102, 74.3441, BigDecimal.valueOf(2));
        MatchDecision decision = GeoCoverageMatcher.evaluate(
                "CUSTOMER_LOCATION", false, null, List.of(area), List.of(), DHA);
        assertFalse(decision.eligible());
        assertEquals("outside_service_area", decision.reason());
    }

    @Test
    void radiusAcceptsInsideCoverage() {
        CoverageArea area = new CoverageArea(
                "RADIUS", null, "Lahore", "Lahore", 31.4697, 74.4105, BigDecimal.valueOf(8));
        MatchDecision decision = GeoCoverageMatcher.evaluate(
                "CUSTOMER_LOCATION", false, null, List.of(area), List.of(), DHA);
        assertTrue(decision.eligible());
        assertEquals("radius", decision.reason());
        assertNotNull(decision.distanceKm());
        assertTrue(decision.distanceKm() < 1);
    }

    @Test
    void hybridAllowsBranchCityWithoutServiceArea() {
        MatchDecision decision = GeoCoverageMatcher.evaluate(
                "HYBRID",
                false,
                null,
                List.of(),
                List.of(new BranchLocation("Lahore", 31.52, 74.35)),
                DHA);
        assertTrue(decision.eligible());
        assertEquals("branch_city", decision.reason());
    }

    @Test
    void atProviderDoesNotMatchOtherCity() {
        MatchDecision decision = GeoCoverageMatcher.evaluate(
                "AT_PROVIDER",
                false,
                null,
                List.of(),
                List.of(new BranchLocation("Karachi", 24.86, 67.00)),
                DHA);
        assertFalse(decision.eligible());
    }

    @Test
    void remoteIsAlwaysEligible() {
        MatchDecision decision = GeoCoverageMatcher.evaluate(
                "REMOTE", false, null, List.of(), List.of(), DHA);
        assertTrue(decision.eligible());
        assertEquals("remote", decision.reason());
    }
}
