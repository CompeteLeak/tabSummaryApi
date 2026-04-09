package com.competeleak.summarizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Value("${lemonsqueezy.webhook.secret}")
    private String webhookSecret;

    public WebhookService(UserService userService, ObjectMapper objectMapper) {
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    public void processLemonSqueezyEvent(String rawPayload, String signature) {
        verifySignature(rawPayload, signature);

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String eventName = root.path("meta").path("event_name").asText();
            JsonNode attributes = root.path("data").path("attributes");

            log.info("Received Lemon Squeezy event: {}", eventName);

            switch (eventName) {
                case "order_created"           -> handleOrderCreated(attributes);
                case "subscription_created"    -> handleSubscriptionCreated(attributes, root);
                case "subscription_cancelled",
                     "subscription_expired",
                     "subscription_paused"     -> handleSubscriptionCancelled(root);
                default -> log.info("Unhandled Lemon Squeezy event: {}", eventName);
            }

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to process Lemon Squeezy webhook payload", e);
            throw new RuntimeException("Webhook processing failed", e);
        }
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    /**
     * One-time purchase — upgrade the buyer to PAID.
     * Uses the order identifier as the subscription reference.
     */
    private void handleOrderCreated(JsonNode attributes) {
        String email   = attributes.path("user_email").asText();
        String orderId = attributes.path("identifier").asText();

        if (email.isBlank()) {
            log.warn("order_created missing user_email — skipping");
            return;
        }

        userService.createOrUpgradePaidUser(email, orderId);
        log.info("Upgraded {} to PAID via order {}", email, orderId);
    }

    /**
     * Recurring subscription started — upgrade the subscriber to PAID.
     */
    private void handleSubscriptionCreated(JsonNode attributes, JsonNode root) {
        String email          = attributes.path("user_email").asText();
        String subscriptionId = root.path("data").path("id").asText();

        if (email.isBlank()) {
            log.warn("subscription_created missing user_email — skipping");
            return;
        }

        userService.createOrUpgradePaidUser(email, subscriptionId);
        log.info("Upgraded {} to PAID via subscription {}", email, subscriptionId);
    }

    /**
     * Subscription cancelled / expired / paused — downgrade to FREE.
     */
    private void handleSubscriptionCancelled(JsonNode root) {
        String subscriptionId = root.path("data").path("id").asText();

        if (subscriptionId.isBlank()) {
            log.warn("Subscription cancellation event missing id — skipping");
            return;
        }

        userService.downgradeToFree(subscriptionId);
        log.info("Downgraded subscription {} to FREE", subscriptionId);
    }

    // ── HMAC-SHA256 signature verification ────────────────────────────────────

    /**
     * Lemon Squeezy signs the raw request body with your webhook secret.
     * We recompute the HMAC and compare using a timing-safe comparison
     * to prevent timing attacks.
     *
     * Throws SecurityException on mismatch — the controller returns 500,
     * which tells Lemon Squeezy to retry. A 200 only leaves if fully valid.
     */
    private void verifySignature(String rawPayload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(
                    mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8)));

            if (!constantTimeEquals(expected, signature)) {
                log.warn("Invalid Lemon Squeezy webhook signature received");
                throw new SecurityException("Invalid webhook signature");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Signature verification error", e);
        }
    }

    /**
     * Timing-safe string comparison — prevents timing side-channel attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
