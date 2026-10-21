package com.example.twitter_challenge;

import com.example.twitter_challenge.Service.interfaces.FeedService;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.controller.FeedController;
import com.example.twitter_challenge.dto.response.StatisticsResponse;
import com.example.twitter_challenge.dto.response.TweetResponse;
import com.example.twitter_challenge.dto.response.UserResponse;
import com.example.twitter_challenge.security.config.SecurityConfig;
import com.example.twitter_challenge.security.filter.JwtAuthenticationFilter;
import com.example.twitter_challenge.security.service.CustomUserDetailsService;
import com.example.twitter_challenge.security.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FeedController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FeedControllerExploreTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeedService feedService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        when(userService.findByEmail("alice@example.com"))
                .thenReturn(new UserResponse(1L, "alice", "alice@example.com", null,
                        null, null, null, null, false));
    }

    @Test
    void exploreRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/feed/explore"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exploreReturnsTheTweetsWithTheirAuthorAndPremiumInformation() throws Exception {
        when(feedService.getExploreTweets(any())).thenReturn(new PageImpl<>(
                List.of(new TweetResponse(
                        10L,
                        "explore me",
                        2L,
                        "bob",
                        "bob.png",
                        true,
                        null,
                        null,
                        null,
                        new StatisticsResponse(10L, 1L, 2L, 3L, 4L, 5L, 6L)
                )),
                PageRequest.of(0, 20),
                1
        ));

        mockMvc.perform(get("/feed/explore")
                        .with(SecurityMockMvcRequestPostProcessors.user("alice@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].content").value("explore me"))
                .andExpect(jsonPath("$.content[0].userId").value(2))
                .andExpect(jsonPath("$.content[0].authorUsername").value("bob"))
                .andExpect(jsonPath("$.content[0].authorPhoto").value("bob.png"))
                .andExpect(jsonPath("$.content[0].premium").value(true))
                .andExpect(jsonPath("$.content[0].statistics.likes").value(2))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(feedService).getExploreTweets(any());
    }

    @Test
    void exploreIsIndependentOfTheFollowGraphOfTheCaller() throws Exception {
        when(feedService.getExploreTweets(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/feed/explore")
                        .with(SecurityMockMvcRequestPostProcessors.user("alice@example.com")))
                .andExpect(status().isOk());

        verify(feedService).getExploreTweets(any());
    }
}
