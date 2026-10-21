package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Service.interfaces.StripeService;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.controller.StripeController;
import com.example.twitter_challenge.dto.response.StripeConfigResponse;
import com.example.twitter_challenge.dto.response.UserResponse;
import com.example.twitter_challenge.exception.ActiveSubscriptionExistsException;
import com.example.twitter_challenge.exception.StripeCustomerException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import com.example.twitter_challenge.security.config.CustomUserDetails;
import com.example.twitter_challenge.security.config.SecurityConfig;
import com.example.twitter_challenge.security.filter.JwtAuthenticationFilter;
import com.example.twitter_challenge.security.service.CustomUserDetailsService;
import com.example.twitter_challenge.security.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StripeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class StripeControllerSecurityTest {

    private static final String AUTHENTICATED_EMAIL = "alice@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StripeService stripeService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        when(userService.findByEmail(AUTHENTICATED_EMAIL))
                .thenReturn(new UserResponse(
                        1L,
                        "alice",
                        AUTHENTICATED_EMAIL,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false
                ));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_EMAIL)
    void userCanCreateSubscriptionForOwnAccount() throws Exception {
        when(stripeService.createSubscription(1L)).thenReturn("sub_1");

        mockMvc.perform(post("/stripe/subscription/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("sub_1"));

        verify(stripeService).createSubscription(1L);
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_EMAIL)
    void duplicateOrInProgressSubscriptionReturnsConflict() throws Exception {
        when(stripeService.createSubscription(1L))
                .thenThrow(new ActiveSubscriptionExistsException("already active or in progress"));

        mockMvc.perform(post("/stripe/subscription/1"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_EMAIL)
    void userCannotCreateSubscriptionForAnotherAccount() throws Exception {
        mockMvc.perform(post("/stripe/subscription/2"))
                .andExpect(status().isForbidden());

        verify(stripeService, never()).createSubscription(anyLong());
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_EMAIL)
    void userCanAttachPaymentMethodForOwnAccount() throws Exception {
        mockMvc.perform(post("/stripe/payment-method/1")
                        .param("paymentMethodId", "pm_1"))
                .andExpect(status().isOk());

        verify(stripeService).attachPaymentMethod(1L, "pm_1");
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_EMAIL)
    void userCannotAttachPaymentMethodForAnotherAccount() throws Exception {
        mockMvc.perform(post("/stripe/payment-method/2")
                        .param("paymentMethodId", "pm_1"))
                .andExpect(status().isForbidden());

        verify(stripeService, never()).attachPaymentMethod(anyLong(), any());
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_EMAIL)
    void userCanCreateSetupIntentForOwnAccount() throws Exception {
        when(stripeService.createSetupIntent(1L)).thenReturn("seti_secret");

        mockMvc.perform(post("/stripe/setup-intent/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("seti_secret"));

        verify(stripeService).createSetupIntent(1L);
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_EMAIL)
    void userCannotCreateSetupIntentForAnotherAccount() throws Exception {
        mockMvc.perform(post("/stripe/setup-intent/2"))
                .andExpect(status().isForbidden());

        verify(stripeService, never()).createSetupIntent(anyLong());
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_EMAIL)
    void customerCreationUsesAuthenticatedIdentityAndIgnoresClientParameters() throws Exception {
        when(stripeService.createCustomer(1L)).thenReturn("cus_1");

        mockMvc.perform(post("/stripe/customer")
                        .param("email", "bob@example.com")
                        .param("username", "bob"))
                .andExpect(status().isOk())
                .andExpect(content().string("cus_1"));

        verify(stripeService).createCustomer(1L);
    }

    @Test
    void verifiedJwtPrincipalResolvesToAuthenticatedUser() throws Exception {
        when(jwtService.extractEmail("valid-token")).thenReturn(AUTHENTICATED_EMAIL);
        when(customUserDetailsService.loadUserByUsername(AUTHENTICATED_EMAIL))
                .thenReturn(new CustomUserDetails(enabledUser(1L, AUTHENTICATED_EMAIL)));
        when(stripeService.createSubscription(1L)).thenReturn("sub_1");

        mockMvc.perform(post("/stripe/subscription/1")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("sub_1"));

        verify(stripeService).createSubscription(1L);
    }

    @Test
    void verifiedJwtUserCannotActAsAnotherUser() throws Exception {
        when(jwtService.extractEmail("valid-token")).thenReturn(AUTHENTICATED_EMAIL);
        when(customUserDetailsService.loadUserByUsername(AUTHENTICATED_EMAIL))
                .thenReturn(new CustomUserDetails(enabledUser(1L, AUTHENTICATED_EMAIL)));

        mockMvc.perform(post("/stripe/subscription/2")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());

        verify(stripeService, never()).createSubscription(anyLong());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/stripe/subscription/1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(stripeService);
    }

    @Test
    @WithMockUser(username = "missing@example.com")
    void unknownAuthenticatedUserKeepsNotFoundBehavior() throws Exception {
        when(userService.findByEmail("missing@example.com"))
                .thenThrow(new UserNotFoundException("User with email missing@example.com not found"));

        mockMvc.perform(post("/stripe/subscription/1"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(stripeService);
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_EMAIL)
    void configExposesOnlyThePublishableKeyAndMode() throws Exception {
        when(stripeService.getStripeConfig())
                .thenReturn(new StripeConfigResponse("pk_test_123", "test"));

        mockMvc.perform(get("/stripe/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishableKey").value("pk_test_123"))
                .andExpect(jsonPath("$.mode").value("test"))
                .andExpect(jsonPath("$.secretKey").doesNotExist());
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_EMAIL)
    void customerOwnershipConflictReturnsAClear409() throws Exception {
        when(stripeService.createCustomer(1L)).thenThrow(
                new StripeCustomerException(
                        "The Stripe customer stored for this account is not verifiably linked to it."
                )
        );

        mockMvc.perform(post("/stripe/customer"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "The Stripe customer stored for this account is not verifiably linked to it."
                ));
    }

    private User enabledUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setIsDeleted(false);
        return user;
    }
}
