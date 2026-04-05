package com.competeleak.summarizer.repository;

import com.competeleak.summarizer.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByApiKey(String apiKey);

    Optional<User> findByEmail(String email);

    Optional<User> findByLemonSqueezySubscriptionId(String subscriptionId);
}
