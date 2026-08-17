package com.hourslot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hourslot.model.SystemSetting;
import com.hourslot.model.User;
import com.hourslot.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;
    private final ObjectMapper objectMapper;

    public SystemSettingService(SystemSettingRepository systemSettingRepository, ObjectMapper objectMapper) {
        this.systemSettingRepository = systemSettingRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> asAdminSettings() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("defaultCommissionRate", 0.0);
        body.put("supportedCurrencies", readString("supported_currencies", "USD,PKR,AED,EUR,GBP"));
        body.put("registrationOpen", readBoolean("registration_open", true));
        body.put("defaultCurrency", readString("default_currency", "USD"));
        return body;
    }

    @Transactional
    public Map<String, Object> updateAdminSettings(boolean registrationOpen, String supportedCurrencies, User actor) {
        write("registration_open", registrationOpen, actor);
        write("supported_currencies", supportedCurrencies, actor);
        return asAdminSettings();
    }

    private boolean readBoolean(String key, boolean fallback) {
        return systemSettingRepository.findById(key)
                .map(setting -> {
                    try {
                        JsonNode node = objectMapper.readTree(setting.getValue());
                        if (node.isBoolean()) {
                            return node.booleanValue();
                        }
                        return Boolean.parseBoolean(node.asText(String.valueOf(fallback)));
                    } catch (Exception e) {
                        return fallback;
                    }
                })
                .orElse(fallback);
    }

    private String readString(String key, String fallback) {
        return systemSettingRepository.findById(key)
                .map(setting -> {
                    try {
                        JsonNode node = objectMapper.readTree(setting.getValue());
                        return node.isTextual() ? node.asText() : node.toString().replace("\"", "");
                    } catch (Exception e) {
                        return fallback;
                    }
                })
                .orElse(fallback);
    }

    private void write(String key, Object value, User actor) {
        try {
            SystemSetting setting = systemSettingRepository.findById(key)
                    .orElse(SystemSetting.builder().key(key).build());
            setting.setValue(objectMapper.writeValueAsString(value));
            setting.setUpdatedBy(actor);
            systemSettingRepository.save(setting);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist setting " + key, e);
        }
    }
}
