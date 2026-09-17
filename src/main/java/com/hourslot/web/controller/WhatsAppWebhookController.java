package com.hourslot.web.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/whatsapp")
public class WhatsAppWebhookController {
    private static final Logger log = LogManager.getLogger(WhatsAppWebhookController.class);

    @GetMapping("/webhook")
    public ResponseEntity<?> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        try {
            log.info("WhatsApp webhook verify mode={}", mode);
            if ("subscribe".equalsIgnoreCase(mode) && challenge != null) {
                return ResponseEntity.ok(challenge);
            }
            return ResponseEntity.ok(Map.of("status", "ignored"));
        } catch (Exception ex) {
            log.warn("WhatsApp verify error (swallowed): {}", ex.getMessage());
            return ResponseEntity.ok(Map.of("status", "error_acknowledged"));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> receive(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            log.debug("WhatsApp webhook payload received keys={}",
                    payload == null ? "null" : payload.keySet());
        } catch (Exception ex) {
            log.warn("WhatsApp webhook processing error (swallowed): {}", ex.getMessage());
        }
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("received", true);
        return ResponseEntity.ok(ack);
    }
}
