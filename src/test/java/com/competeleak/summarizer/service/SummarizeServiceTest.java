package com.competeleak.summarizer.service;

import com.competeleak.summarizer.exception.RateLimitExceededException;
import com.competeleak.summarizer.model.User;
import com.competeleak.summarizer.repository.UsageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SummarizeServiceTest {

    @Mock
    private RestClient claudeRestClient;

    @Mock
    private UsageRepository usageRepository;

    @InjectMocks
    private SummarizeService summarizeService;

    private static final int FREE_LIMIT = 20;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(summarizeService, "freeTierLimit", FREE_LIMIT);
        ReflectionTestUtils.setField(summarizeService, "claudeModel", "claude-opus-4-6");
        ReflectionTestUtils.setField(summarizeService, "objectMapper", new ObjectMapper());
    }

    @Test
    void freeUser_underLimit_allowed() {
        User freeUser = freeUser();
        when(usageRepository.countByUserAndBillingMonth(eq(freeUser), any())).thenReturn(5L);

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(summarizeService, "enforceRateLimit", freeUser, "1.2.3.4", "2026-05"));
    }

    @Test
    void freeUser_atLimit_throwsRateLimitExceededException() {
        User freeUser = freeUser();
        when(usageRepository.countByUserAndBillingMonth(eq(freeUser), any())).thenReturn((long) FREE_LIMIT);

        assertThrows(RateLimitExceededException.class, () ->
                ReflectionTestUtils.invokeMethod(summarizeService, "enforceRateLimit", freeUser, "1.2.3.4", "2026-05"));
    }

    @Test
    void paidUser_alwaysAllowed_regardlessOfUsage() {
        User paidUser = paidUser();

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(summarizeService, "enforceRateLimit", paidUser, "1.2.3.4", "2026-05"));

        verifyNoInteractions(usageRepository);
    }

    @Test
    void anonymousUser_underLimit_allowed() {
        when(usageRepository.countByAnonymousIdentifierAndBillingMonth(any(), any())).thenReturn(3L);

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(summarizeService, "enforceRateLimit", null, "1.2.3.4", "2026-05"));
    }

    @Test
    void anonymousUser_atLimit_throwsRateLimitExceededException() {
        when(usageRepository.countByAnonymousIdentifierAndBillingMonth(any(), any())).thenReturn((long) FREE_LIMIT);

        assertThrows(RateLimitExceededException.class, () ->
                ReflectionTestUtils.invokeMethod(summarizeService, "enforceRateLimit", null, "1.2.3.4", "2026-05"));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private User freeUser() {
        User user = new User();
        user.setEmail("free@example.com");
        user.setTier(User.Tier.FREE);
        return user;
    }

    private User paidUser() {
        User user = new User();
        user.setEmail("paid@example.com");
        user.setTier(User.Tier.PAID);
        return user;
    }
}
