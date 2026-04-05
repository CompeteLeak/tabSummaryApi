package com.competeleak.summarizer.service;

import org.springframework.stereotype.Service;

@Service
public class WebhookService {

    // TODO (item 4): inject UserService, verify HMAC signature, process LS events
    // Placeholder — implementation comes in item 4

    public void processLemonSqueezyEvent(String rawPayload, String signature) {
        throw new UnsupportedOperationException("WebhookService not yet implemented");
    }
}
