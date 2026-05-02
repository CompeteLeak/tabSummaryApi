package com.competeleak.summarizer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private WebhookService webhookService;

    private static final String SECRET = "test-secret";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(webhookService, "webhookSecret", SECRET);
        ReflectionTestUtils.setField(webhookService, "objectMapper", objectMapper);
    }

    // ── Signature verification ─────────────────────────────────────────────────

    @Test
    void validSignature_passes() {
        String payload = """
                {"meta":{"event_name":"order_created"},"data":{"attributes":{"user_email":"test@example.com","identifier":"order-1"}}}
                """.strip();
        String sig = hmac(payload, SECRET);

        assertDoesNotThrow(() -> webhookService.processLemonSqueezyEvent(payload, sig));
    }

    @Test
    void invalidSignature_throwsSecurityException() {
        String payload = """
                {"meta":{"event_name":"order_created"},"data":{"attributes":{"user_email":"test@example.com","identifier":"order-1"}}}
                """.strip();

        assertThrows(SecurityException.class,
                () -> webhookService.processLemonSqueezyEvent(payload, "bad-signature"));
    }

    // ── order_created ──────────────────────────────────────────────────────────

    @Test
    void orderCreated_upgradesUser() {
        String payload = """
                {"meta":{"event_name":"order_created"},"data":{"attributes":{"user_email":"buyer@example.com","identifier":"order-99"}}}
                """.strip();

        webhookService.processLemonSqueezyEvent(payload, hmac(payload, SECRET));

        verify(userService).createOrUpgradePaidUser("buyer@example.com", "order-99");
    }

    @Test
    void orderCreated_missingEmail_skipsUpgrade() {
        String payload = """
                {"meta":{"event_name":"order_created"},"data":{"attributes":{"user_email":"","identifier":"order-99"}}}
                """.strip();

        webhookService.processLemonSqueezyEvent(payload, hmac(payload, SECRET));

        verify(userService, never()).createOrUpgradePaidUser(any(), any());
    }

    // ── subscription_created ───────────────────────────────────────────────────

    @Test
    void subscriptionCreated_upgradesUser() {
        String payload = """
                {"meta":{"event_name":"subscription_created"},"data":{"id":"sub-42","attributes":{"user_email":"sub@example.com"}}}
                """.strip();

        webhookService.processLemonSqueezyEvent(payload, hmac(payload, SECRET));

        verify(userService).createOrUpgradePaidUser("sub@example.com", "sub-42");
    }

    // ── subscription_cancelled ─────────────────────────────────────────────────

    @Test
    void subscriptionCancelled_downgradesUser() {
        String payload = """
                {"meta":{"event_name":"subscription_cancelled"},"data":{"id":"sub-42","attributes":{}}}
                """.strip();

        webhookService.processLemonSqueezyEvent(payload, hmac(payload, SECRET));

        verify(userService).downgradeToFree("sub-42");
    }

    @Test
    void unknownEvent_doesNothing() {
        String payload = """
                {"meta":{"event_name":"some_unknown_event"},"data":{"attributes":{}}}
                """.strip();

        webhookService.processLemonSqueezyEvent(payload, hmac(payload, SECRET));

        verifyNoInteractions(userService);
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private static String hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
