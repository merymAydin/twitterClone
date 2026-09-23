package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.dto.request.CreateBookmarkRequest;
import com.example.twitter_challenge.dto.response.BookmarkResponse;

import java.util.List;

public interface BookmarkService {
    BookmarkResponse createBookmark(CreateBookmarkRequest request);
    void removeBookmark(CreateBookmarkRequest request,Long userId);
    List<BookmarkResponse> findAllBookmarks();
    BookmarkResponse findBookmarkById(Long id);
}
