package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.BookmarkService;
import com.example.twitter_challenge.dto.request.CreateBookmarkRequest;
import com.example.twitter_challenge.dto.response.BookmarkResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bookmarks")
public class BookmarkController {
    private final BookmarkService bookmarkService;
    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @PostMapping
    public BookmarkResponse createBookMark(@RequestBody CreateBookmarkRequest request){
        return bookmarkService.createBookMark(request);
    }
}
