package com.competeleak.summarizer.controller;

import com.competeleak.summarizer.filter.ApiKeyAuthFilter;
import com.competeleak.summarizer.model.User;
import com.competeleak.summarizer.service.SummarizeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SummarizeController {

    private final SummarizeService summarizeService;

    public SummarizeController(SummarizeService summarizeService) {
        this.summarizeService = summarizeService;
    }

    public record SummarizeRequest(@NotBlank String url, @NotBlank String content) {}

    @PostMapping("/summarize")
    public ResponseEntity<SummarizeService.SummaryResponse> summarize(
            @Valid @RequestBody SummarizeRequest request,
            HttpServletRequest httpRequest) {

        User user = (User) httpRequest.getAttribute(ApiKeyAuthFilter.USER_ATTRIBUTE);
        SummarizeService.SummaryResponse response = summarizeService.summarize(
                request.url(), request.content(), user);
        return ResponseEntity.ok(response);
    }
}
