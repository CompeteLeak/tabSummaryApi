package com.competeleak.summarizer.controller;

import com.competeleak.summarizer.service.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/lemon-squeezy")
    public ResponseEntity<Void> handleLemonSqueezy(
            @RequestBody String rawPayload,
            @RequestHeader("X-Signature") String signature) {

        webhookService.processLemonSqueezyEvent(rawPayload, signature);
        return ResponseEntity.ok().build();
    }
}
