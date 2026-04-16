package com.competeleak.summarizer.controller;

import com.competeleak.summarizer.filter.ApiKeyAuthFilter;
import com.competeleak.summarizer.model.User;
import com.competeleak.summarizer.repository.UsageRepository;
import com.competeleak.summarizer.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final UsageRepository usageRepository;
    private final UserRepository userRepository;

    @Value("${app.free-tier.monthly-limit}")
    private int freeTierLimit;

    public AccountController(UsageRepository usageRepository, UserRepository userRepository) {
        this.usageRepository = usageRepository;
        this.userRepository = userRepository;
    }

    public record AccountStatus(String tier, long usageCount, int limit) {}

    /**
     * Self-serve key lookup — user enters their purchase email and retrieves their API key.
     * Only returns a key for PAID users.
     */
    @GetMapping("/key-lookup")
    public ResponseEntity<Map<String, String>> keyLookup(@RequestParam String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .filter(u -> u.getTier() == User.Tier.PAID)
                .map(u -> ResponseEntity.ok(Map.of("apiKey", u.getApiKey())))
                .orElse(ResponseEntity.status(404)
                        .body(Map.of("error", "No paid account found for that email. Check your email or complete a purchase.")));
    }

    @GetMapping("/status")
    public ResponseEntity<AccountStatus> status(HttpServletRequest request) {
        User user = (User) request.getAttribute(ApiKeyAuthFilter.USER_ATTRIBUTE);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        String billingMonth = YearMonth.now().toString(); // e.g. "2026-04"
        long usage = usageRepository.countByUserAndBillingMonth(user, billingMonth);
        int limit = user.getTier() == User.Tier.PAID ? Integer.MAX_VALUE : freeTierLimit;

        return ResponseEntity.ok(new AccountStatus(user.getTier().name().toLowerCase(), usage, limit));
    }
}
