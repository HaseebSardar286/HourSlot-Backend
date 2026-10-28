package com.duemet.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.duemet.notification.repository.PushSubscriptionRepository;
import com.duemet.notification.repository.PushSubscriptionRepository.PushSubscription;
import com.duemet.notification.services.WebPushService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Sends a real encrypted push to a local stand-in for a browser push service. */
class WebPushServiceTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Uncompressed P-256 point (0x04 || X || Y), the format browsers and VAPID use. */
    private static byte[] uncompressed(ECPublicKey key) {
        byte[] x = toFixed(key.getW().getAffineX().toByteArray());
        byte[] y = toFixed(key.getW().getAffineY().toByteArray());
        byte[] out = new byte[65];
        out[0] = 4;
        System.arraycopy(x, 0, out, 1, 32);
        System.arraycopy(y, 0, out, 33, 32);
        return out;
    }

    private static byte[] toFixed(byte[] v) {
        byte[] out = new byte[32];
        int copy = Math.min(32, v.length);
        System.arraycopy(v, v.length - copy, out, 32 - copy, copy);
        return out;
    }

    private static byte[] privateScalar(java.security.interfaces.ECPrivateKey key) {
        return toFixed(key.getS().toByteArray());
    }

    @Test
    void deliversEncryptedPushAndDropsGoneSubscriptions() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        var vapid = gen.generateKeyPair();
        var device = gen.generateKeyPair();
        byte[] authSecret = new byte[16];
        new SecureRandom().nextBytes(authSecret);

        Map<String, String> seenHeaders = new ConcurrentHashMap<>();
        AtomicInteger bodyLength = new AtomicInteger();
        CountDownLatch requests = new CountDownLatch(2);
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push", exchange -> {
            exchange.getRequestHeaders().forEach((k, v) -> seenHeaders.put(k.toLowerCase(), v.get(0)));
            bodyLength.set(exchange.getRequestBody().readAllBytes().length);
            // First delivery succeeds; the second reports the subscription is gone.
            exchange.sendResponseHeaders(calls.incrementAndGet() == 1 ? 201 : 410, -1);
            exchange.close();
            requests.countDown();
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/push";

        PushSubscriptionRepository repo = mock(PushSubscriptionRepository.class);
        when(repo.findByUser(7L)).thenReturn(List.of(new PushSubscription(1L, 7L, endpoint,
                b64(uncompressed((ECPublicKey) device.getPublic())), b64(authSecret))));
        WebPushService service = new WebPushService(repo, new ObjectMapper(),
                b64(uncompressed((ECPublicKey) vapid.getPublic())),
                b64(privateScalar((java.security.interfaces.ECPrivateKey) vapid.getPrivate())),
                "mailto:ops@example.com", "http://localhost:3000");

        assertTrue(service.isEnabled());
        service.sendToUser(7L, "Booking confirmed", "Fan installation, Tue 10:00", "/profile/bookings");
        service.sendToUser(7L, "Second", "Again", "/");
        assertTrue(requests.await(15, TimeUnit.SECONDS), "push service was not called");

        assertEquals("aes128gcm", seenHeaders.get("content-encoding"));
        assertTrue(seenHeaders.get("authorization").startsWith("vapid t="), seenHeaders.get("authorization"));
        assertNotNull(seenHeaders.get("ttl"));
        assertTrue(bodyLength.get() > 60, "payload should be encrypted, got " + bodyLength.get() + " bytes");
        verify(repo, timeout(5000)).touch(1L);
        verify(repo, timeout(5000)).delete(1L);
    }

    @Test
    void disabledWithoutKeys() {
        WebPushService service = new WebPushService(mock(PushSubscriptionRepository.class), new ObjectMapper(),
                "", "", "", "http://localhost:3000");
        assertFalse(service.isEnabled());
        assertDoesNotThrow(() -> service.sendToUser(1L, "t", "b", "/"));
    }
}
