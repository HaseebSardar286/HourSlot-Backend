package com.hourslot.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Prevents "could not initialize proxy - no Session" when Jackson
 * serializes entities with uninitialized LAZY associations.
 * Unloaded proxies are written as null (or id-only when enabled).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Hibernate6Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        // Do not open DB connections during JSON writing
        module.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        // Prefer writing the FK id instead of null when the proxy is not loaded
        module.enable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);
        return module;
    }
}
