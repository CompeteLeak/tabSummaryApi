package com.competeleak.summarizer.service;

import com.competeleak.summarizer.model.User;
import com.competeleak.summarizer.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Called by WebhookService after a successful Lemon Squeezy payment.
     * Creates a new paid user or upgrades an existing free user.
     * Returns the API key to be delivered to the customer (e.g. via email).
     */
    @Transactional
    public User createOrUpgradePaidUser(String email, String lemonSqueezySubscriptionId) {
        return userRepository.findByEmail(email)
                .map(existing -> {
                    existing.setTier(User.Tier.PAID);
                    existing.setLemonSqueezySubscriptionId(lemonSqueezySubscriptionId);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail(email);
                    user.setApiKey(generateApiKey());
                    user.setTier(User.Tier.PAID);
                    user.setLemonSqueezySubscriptionId(lemonSqueezySubscriptionId);
                    return userRepository.save(user);
                });
    }

    /**
     * Downgrades a user to FREE tier (e.g. subscription cancelled or payment failed).
     */
    @Transactional
    public void downgradeToFree(String lemonSqueezySubscriptionId) {
        userRepository.findByLemonSqueezySubscriptionId(lemonSqueezySubscriptionId)
                .ifPresent(user -> {
                    user.setTier(User.Tier.FREE);
                    userRepository.save(user);
                });
    }

    private String generateApiKey() {
        // Prefixed for easy identification in logs/support
        return "tsk_" + UUID.randomUUID().toString().replace("-", "");
    }
}
