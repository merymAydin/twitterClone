package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.interfaces.BookmarkService;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.dto.request.CreateBookmarkRequest;
import com.example.twitter_challenge.dto.response.BookmarkResponse;
import com.example.twitter_challenge.dto.response.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bookmarks")
public class BookmarkController {
    private final BookmarkService bookmarkService;
    private final UserService userService;

    public BookmarkController(BookmarkService bookmarkService, UserService userService) {
        this.bookmarkService = bookmarkService;
        this.userService = userService;
    }

    @PostMapping
    public BookmarkResponse createBookMark(@Valid @RequestBody CreateBookmarkRequest request){
        return bookmarkService.createBookmark(request);
    }
    @DeleteMapping
    public ResponseEntity<Void> deleteBookmark(@Valid @RequestBody CreateBookmarkRequest request){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (String) authentication.getPrincipal();
        UserResponse user = userService.findByUserName(username);
        bookmarkService.removeBookmark(request,user.userId());
        return ResponseEntity.noContent().build();
    }
    @GetMapping
    public List<BookmarkResponse> getAllBookmarks(){
        return bookmarkService.findAllBookmarks();
    }
    @GetMapping("/{id}")
    public BookmarkResponse getBookmarkById(@PathVariable Long id){
        return bookmarkService.findBookmarkById(id);
    }
}
