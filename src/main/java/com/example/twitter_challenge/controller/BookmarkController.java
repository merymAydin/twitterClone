package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.interfaces.BookmarkService;
import com.example.twitter_challenge.dto.request.CreateBookmarkRequest;
import com.example.twitter_challenge.dto.response.BookmarkResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
    public BookmarkResponse createBookMark(@Valid @RequestBody CreateBookmarkRequest request){
        return bookmarkService.createBookmark(request);
    }
    @DeleteMapping
    public ResponseEntity<Void> deleteBookmark(@Valid @RequestBody CreateBookmarkRequest request){
        bookmarkService.removeBookmark(request);
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
