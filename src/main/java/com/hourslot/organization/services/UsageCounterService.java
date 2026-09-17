package com.hourslot.organization.services;

import com.hourslot.organization.model.Organization;
import com.hourslot.organization.repository.UsageCounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class UsageCounterService {
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UsageCounterRepository repository;

    public UsageCounterService(UsageCounterRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void increment(Organization organization, String metricCode) {
        increment(organization, metricCode, 1);
    }

    @Transactional
    public void increment(Organization organization, String metricCode, long delta) {
        if (organization == null || organization.getId() == null || metricCode == null) {
            return;
        }
        repository.increment(organization.getId(), currentPeriod(), metricCode, delta);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> currentMonthUsage(Organization organization) {
        String period = currentPeriod();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("organizationId", organization.getId());
        result.put("counters", repository.findForPeriod(organization.getId(), period));
        return result;
    }

    @Transactional(readOnly = true)
    public long currentCount(Organization organization, String metricCode) {
        if (organization == null || organization.getId() == null || metricCode == null) {
            return 0L;
        }
        return repository.get(organization.getId(), currentPeriod(), metricCode);
    }

    private static String currentPeriod() {
        return LocalDate.now().format(PERIOD);
    }
}
