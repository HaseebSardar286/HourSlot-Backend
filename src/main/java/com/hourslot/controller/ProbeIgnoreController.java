package com.hourslot.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Absorbs external tooling probes (e.g. /audit/dashboard-data on localhost:8080)
 * so they do not spam logs as 401/404 exceptions.
 */
@RestController
public class ProbeIgnoreController {

    @RequestMapping(value = "/audit/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.HEAD})
    public ResponseEntity<Void> ignoreAuditProbes() {
        return ResponseEntity.noContent().build();
    }
}
