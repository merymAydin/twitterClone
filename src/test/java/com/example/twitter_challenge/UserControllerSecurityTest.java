package com.example.twitter_challenge;

import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.controller.UserController;
import com.example.twitter_challenge.dto.response.PublicUserResponse;
import com.example.twitter_challenge.dto.response.UserResponse;
import com.example.twitter_challenge.exception.ForbiddenException;
import com.example.twitter_challenge.exception.UserAlreadyExistsException;
import com.example.twitter_challenge.security.config.SecurityConfig;
import com.example.twitter_challenge.security.filter.JwtAuthenticationFilter;
import com.example.twitter_challenge.security.service.CustomUserDetailsService;
import com.example.twitter_challenge.security.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void unauthenticatedUserListingIsRejected() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void unauthenticatedProfileReadIsRejected() throws Exception {
        mockMvc.perform(get("/users/1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void publicProfileDoesNotExposeEmailOrBirthday() throws Exception {
        when(userService.findPublicById(1L))
                .thenReturn(new PublicUserResponse(1L, "alice", null, "bio", null, null, false));

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.birthday").doesNotExist());
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void currentUserProfileStillContainsOwnEmail() throws Exception {
        when(userService.findCurrentUser())
                .thenReturn(new UserResponse(1L, "alice", "alice@example.com", null,
                        null, null, null, null, false));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void registrationRemainsPublicButValidated() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest());

        verify(userService, never()).save(any());
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        when(userService.save(any()))
                .thenThrow(new UserAlreadyExistsException("Email is already registered"));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","email":"alice@example.com","password":"password123"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "attacker@example.com")
    void updatingAnotherProfileReturnsForbidden() throws Exception {
        when(userService.update(anyLong(), any()))
                .thenThrow(new ForbiddenException("You are not allowed to modify this user"));

        mockMvc.perform(put("/users/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"attacker","email":"attacker@example.com"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "attacker@example.com")
    void deletingAnotherProfileReturnsForbidden() throws Exception {
        org.mockito.Mockito.doThrow(new ForbiddenException("You are not allowed to modify this user"))
                .when(userService).removeUser(2L);

        mockMvc.perform(delete("/users/2"))
                .andExpect(status().isForbidden());
    }
}
