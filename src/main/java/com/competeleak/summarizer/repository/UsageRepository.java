package com.competeleak.summarizer.repository;

import com.competeleak.summarizer.model.UsageRecord;
import com.competeleak.summarizer.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageRepository extends JpaRepository<UsageRecord, Long> {

    long countByUserAndBillingMonth(User user, String billingMonth);

    long countByAnonymousIdentifierAndBillingMonth(String anonymousIdentifier, String billingMonth);
}
