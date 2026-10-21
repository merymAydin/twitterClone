package com.example.twitter_challenge;

import com.example.twitter_challenge.Service.interfaces.BookmarkService;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.controller.BookmarkController;
import com.example.twitter_challenge.dto.response.UserResponse;
import com.example.twitter_challenge.exception.BookmarkNotFoundException;
import com.example.twitter_challenge.security.config.SecurityConfig;
import com.example.twitter_challenge.security.filter.JwtAuthenticationFilter;
import com.example.twitter_challenge.security.service.CustomUserDetailsService;
import com.example.twitter_challenge.security.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BookmarkController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class BookmarkControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookmarkService bookmarkService;

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
    void bookmarksAreAlwaysQueriedForTheAuthenticatedUser() throws Exception {
        when(bookmarkService.findAllBookmarks(1L)).thenReturn(List.of());

        mockMvc.perform(get("/bookmarks")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("alice@example.com")))
                .andExpect(status().isOk());

        verify(bookmarkService).findAllBookmarks(1L);
        verify(bookmarkService, never()).findAllBookmarks(2L);
    }

    @Test
    void bookmarkLookupUsesAuthenticatedUserId() throws Exception {
        when(bookmarkService.findBookmarkById(99L, 1L))
                .thenThrow(new BookmarkNotFoundException("Bookmark 99 not found"));

        mockMvc.perform(get("/bookmarks/99")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("alice@example.com")))
                .andExpect(status().isNotFound());

        verify(bookmarkService).findBookmarkById(99L, 1L);
    }

    @Test
    void unauthenticatedBookmarkAccessIsRejected() throws Exception {
        mockMvc.perform(get("/bookmarks"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bookmarkService);
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void deletingBookmarkUsesAuthenticatedUserId() throws Exception {
        mockMvc.perform(delete("/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tweetId\":10}"))
                .andExpect(status().isNoContent());

        verify(bookmarkService).removeBookmark(any(), org.mockito.ArgumentMatchers.eq(1L));
    }
}
