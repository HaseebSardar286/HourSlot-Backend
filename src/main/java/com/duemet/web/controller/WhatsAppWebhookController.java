package com.duemet.web.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/whatsapp")
public class WhatsAppWebhookController {
    private static final Logger log = LogManager.getLogger(WhatsAppWebhookController.class);

    @Value("${app.whatsapp.verify-token:}")
    private String verifyToken;

    @Value("${app.whatsapp.app-secret:}")
    private String appSecret;

    @GetMapping("/webhook")
    public ResponseEntity<?> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        log.info("WhatsApp webhook verify mode={}", mode);
        if (verifyToken == null || verifyToken.isBlank()) {
            log.warn("WhatsApp verify rejected: WHATSAPP_VERIFY_TOKEN is not configured");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if ("subscribe".equalsIgnoreCase(mode) && challenge != null && constantTimeEquals(verifyToken, token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> receive(
            @RequestBody(required = false) String payload,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
        if (appSecret == null || appSecret.isBlank() || !validSignature(payload, signature)) {
            log.warn("WhatsApp webhook rejected: missing or invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.debug("WhatsApp webhook payload received bytes={}", payload == null ? 0 : payload.length());
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("received", true);
        return ResponseEntity.ok(ack);
    }

    private boolean validSignature(String payload, String signature) {
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8));
            return constantTimeEquals(HexFormat.of().formatHex(expected), signature.substring(7).toLowerCase());
        } catch (Exception ex) {
            log.warn("WhatsApp signature check failed: {}", ex.getMessage());
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
