package com.competeleak.summarizer.service;

import com.competeleak.summarizer.model.User;
import com.competeleak.summarizer.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void newEmail_createsUserAsPaid() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User result = userService.createOrUpgradePaidUser("new@example.com", "sub-1");

        assertEquals(User.Tier.PAID, result.getTier());
        assertEquals("new@example.com", result.getEmail());
        assertNotNull(result.getApiKey());
        assertTrue(result.getApiKey().startsWith("tsk_"));
    }

    @Test
    void existingFreeUser_upgradesToPaid() {
        User existing = new User();
        existing.setEmail("free@example.com");
        existing.setTier(User.Tier.FREE);
        existing.setApiKey("tsk_existingkey");

        when(userRepository.findByEmail("free@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User result = userService.createOrUpgradePaidUser("free@example.com", "sub-2");

        assertEquals(User.Tier.PAID, result.getTier());
        assertEquals("tsk_existingkey", result.getApiKey()); // key preserved
    }

    @Test
    void downgradeToFree_setsUserToFree() {
        User paidUser = new User();
        paidUser.setTier(User.Tier.PAID);
        paidUser.setLemonSqueezySubscriptionId("sub-99");

        when(userRepository.findByLemonSqueezySubscriptionId("sub-99")).thenReturn(Optional.of(paidUser));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        userService.downgradeToFree("sub-99");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(User.Tier.FREE, captor.getValue().getTier());
    }

    @Test
    void downgradeToFree_unknownSubscriptionId_doesNothing() {
        when(userRepository.findByLemonSqueezySubscriptionId("unknown")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> userService.downgradeToFree("unknown"));
        verify(userRepository, never()).save(any());
    }
}
