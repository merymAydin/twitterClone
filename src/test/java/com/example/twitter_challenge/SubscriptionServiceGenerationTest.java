package com.example.twitter_challenge;

import com.example.twitter_challenge.Repository.SubscriptionRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.SubscriptionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceGenerationTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    @Test
    void generationIsNextPersistedSubscriptionCount() {
        when(subscriptionRepository.countByUserId(7L)).thenReturn(2L);

        assertEquals(3L, subscriptionService.nextCreationGeneration(7L));
    }

    @Test
    void nullUserIdIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> subscriptionService.nextCreationGeneration(null)
        );
    }
}
