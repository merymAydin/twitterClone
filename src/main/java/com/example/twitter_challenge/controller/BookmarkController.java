package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.interfaces.BookmarkService;
import com.example.twitter_challenge.dto.request.CreateBookmarkRequest;
import com.example.twitter_challenge.dto.response.BookmarkResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bookmarks")
public class BookmarkController {
    private final BookmarkService bookmarkService;
    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @PostMapping
    public BookmarkResponse createBookMark(@RequestBody CreateBookmarkRequest request){
        return bookmarkService.createBookmark(request);
    }
    @DeleteMapping
    public void deleteBookmark(@RequestBody CreateBookmarkRequest request){
        bookmarkService.removeBookmark(request);
    }
    @GetMapping
    public List<BookmarkResponse> findAllBookmarks(){
        return bookmarkService.findAllBookmarks();
    }
    @GetMapping("/{id}")
    public BookmarkResponse findBookmarkById(@PathVariable Long id){
        return bookmarkService.findBookmarkById(id);
    }
}
