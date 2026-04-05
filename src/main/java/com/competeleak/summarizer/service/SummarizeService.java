package com.competeleak.summarizer.service;

import com.competeleak.summarizer.model.User;
import org.springframework.stereotype.Service;

@Service
public class SummarizeService {

    // TODO (item 3): inject claudeRestClient and UsageRepository
    // Placeholder — implementation comes in item 3

    public record SummaryResponse(String title, String summary, java.util.List<String> keyPoints) {}

    public SummaryResponse summarize(String url, String content, User user) {
        throw new UnsupportedOperationException("SummarizeService not yet implemented");
    }
}
