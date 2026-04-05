package com.competeleak.summarizer.service;

import com.competeleak.summarizer.exception.RateLimitExceededException;
import com.competeleak.summarizer.model.UsageRecord;
import com.competeleak.summarizer.model.User;
import com.competeleak.summarizer.repository.UsageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;


@Service
public class SummarizeService {

    // Claude will only ever see this many characters of page content.
    // ~6000 chars ≈ ~1500 tokens — keeps costs low and stays well within limits.
    private static final int MAX_CONTENT_LENGTH = 6000;

    private static final String SYSTEM_PROMPT = """
            You are a web page summarization assistant.
            The user will provide the raw text content of a web page and its URL.
            Respond with ONLY valid JSON — no markdown, no code blocks, no extra text.
            Use this exact structure:
            {
              "title": "concise descriptive title for the page",
              "summary": "2-3 sentence summary of the main content",
              "keyPoints": ["key point 1", "key point 2", "key point 3"]
            }
            Keep keyPoints to 3-5 items. Be concise and factual.
            """;

    private final RestClient claudeRestClient;
    private final UsageRepository usageRepository;
    private final ObjectMapper objectMapper;

    @Value("${claude.model}")
    private String claudeModel;

    @Value("${app.free-tier.monthly-limit}")
    private int freeTierLimit;

    public SummarizeService(@Qualifier("claudeRestClient") RestClient claudeRestClient,
                            UsageRepository usageRepository,
                            ObjectMapper objectMapper) {
        this.claudeRestClient  = claudeRestClient;
        this.usageRepository   = usageRepository;
        this.objectMapper      = objectMapper;
    }

    public record SummaryResponse(String title, String summary, List<String> keyPoints) {}

    /**
     * Summarizes page content via the Claude API.
     *
     * @param url         the source URL (included in the prompt for context)
     * @param content     raw visible text extracted from the page
     * @param user        authenticated user, or null for anonymous free-tier requests
     * @param clientIp    raw client IP — hashed for anonymous usage tracking
     */
    public SummaryResponse summarize(String url, String content, User user, String clientIp) {
        String billingMonth = YearMonth.now().toString(); // e.g. "2026-04"

        enforceRateLimit(user, clientIp, billingMonth);

        String truncated   = truncate(content);
        String userMessage = "URL: " + url + "\n\nPage content:\n" + truncated;
        String rawResponse = callClaude(userMessage);
        SummaryResponse result = parseClaudeResponse(rawResponse);

        recordUsage(user, clientIp, billingMonth);
        return result;
    }

    // ── Rate limiting ─────────────────────────────────────────────────────────

    private void enforceRateLimit(User user, String clientIp, String billingMonth) {
        if (user != null && user.getTier() == User.Tier.PAID) {
            return; // paid users are never limited
        }

        long usageCount = (user != null)
                ? usageRepository.countByUserAndBillingMonth(user, billingMonth)
                : usageRepository.countByAnonymousIdentifierAndBillingMonth(
                        hashIp(clientIp), billingMonth);

        if (usageCount >= freeTierLimit) {
            throw new RateLimitExceededException(freeTierLimit);
        }
    }

    // ── Claude API call ───────────────────────────────────────────────────────

    // Typed records for the Claude Messages API — avoids raw Map and null-safety warnings
    private record ClaudeMessage(String role, String content) {}
    private record ClaudeRequest(String model, int max_tokens, String system, List<ClaudeMessage> messages) {}

    private String callClaude(String userMessage) {
        ClaudeRequest requestBody = new ClaudeRequest(
                claudeModel,
                1024,
                SYSTEM_PROMPT,
                List.of(new ClaudeMessage("user", userMessage))
        );

        String responseBody = claudeRestClient.post()
                .body(requestBody)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("content").get(0).path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Claude API response", e);
        }
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    private SummaryResponse parseClaudeResponse(String raw) {
        // Strip markdown code fences if Claude wraps the JSON despite instructions
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
        }

        try {
            JsonNode node = objectMapper.readTree(json);

            String title   = node.path("title").asText("Untitled");
            String summary = node.path("summary").asText("");

            List<String> keyPoints = new ArrayList<>();
            JsonNode kpNode = node.path("keyPoints");
            if (kpNode.isArray()) {
                kpNode.forEach(kp -> keyPoints.add(kp.asText()));
            }

            return new SummaryResponse(title, summary, keyPoints);
        } catch (Exception e) {
            // Claude returned something unparseable — surface the raw text gracefully
            return new SummaryResponse("Summary", raw, List.of());
        }
    }

    // ── Usage recording ───────────────────────────────────────────────────────

    private void recordUsage(User user, String clientIp, String billingMonth) {
        UsageRecord record = new UsageRecord();
        record.setBillingMonth(billingMonth);

        if (user != null) {
            record.setUser(user);
        } else {
            record.setAnonymousIdentifier(hashIp(clientIp));
        }

        usageRepository.save(record);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String truncate(String content) {
        if (content == null) return "";
        return content.length() > MAX_CONTENT_LENGTH
                ? content.substring(0, MAX_CONTENT_LENGTH)
                : content;
    }

    private String hashIp(String ip) {
        if (ip == null) return "unknown";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return ip; // SHA-256 always available in JDK — this won't happen
        }
    }
}
