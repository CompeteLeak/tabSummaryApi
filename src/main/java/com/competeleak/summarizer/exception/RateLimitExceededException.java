package com.competeleak.summarizer.exception;

public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(int limit) {
        super("Free tier limit of " + limit + " summaries/month reached. Upgrade to continue.");
    }
}
