package com.competeleak.summarizer.controller;

import com.competeleak.summarizer.filter.ApiKeyAuthFilter;
import com.competeleak.summarizer.model.User;
import com.competeleak.summarizer.repository.UsageRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final UsageRepository usageRepository;

    @Value("${app.free-tier.monthly-limit}")
    private int freeTierLimit;

    public AccountController(UsageRepository usageRepository) {
        this.usageRepository = usageRepository;
    }

    public record AccountStatus(String tier, long usageCount, int limit) {}

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
