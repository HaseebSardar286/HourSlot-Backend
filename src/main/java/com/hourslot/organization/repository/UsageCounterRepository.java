package com.hourslot.organization.repository;

import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class UsageCounterRepository {
    private final JdbcSupport jdbc;

    public UsageCounterRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public void increment(Long organizationId, String periodYm, String metricCode, long delta) {
        jdbc.update("""
                INSERT INTO organization_usage_counters (organization_id, period_ym, metric_code, count, updated_at)
                VALUES (:organizationId, :periodYm, :metricCode, :delta, NOW())
                ON CONFLICT (organization_id, period_ym, metric_code)
                DO UPDATE SET count = organization_usage_counters.count + :delta, updated_at = NOW()
                """, jdbc.params()
                .addValue("organizationId", organizationId)
                .addValue("periodYm", periodYm)
                .addValue("metricCode", metricCode)
                .addValue("delta", delta));
    }

    public Map<String, Long> findForPeriod(Long organizationId, String periodYm) {
        List<Map<String, Object>> rows = jdbc.jdbc().queryForList("""
                SELECT metric_code, count FROM organization_usage_counters
                WHERE organization_id = :organizationId AND period_ym = :periodYm
                ORDER BY metric_code
                """, Map.of("organizationId", organizationId, "periodYm", periodYm));
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(String.valueOf(row.get("metric_code")), ((Number) row.get("count")).longValue());
        }
        return result;
    }

    public long get(Long organizationId, String periodYm, String metricCode) {
        Long value = findForPeriod(organizationId, periodYm).get(metricCode);
        return value == null ? 0L : value;
    }
}
