package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.dto.request.CreateBookmarkRequest;
import com.example.twitter_challenge.dto.response.BookmarkResponse;

public interface BookmarkService {
    BookmarkResponse createBookMark(CreateBookmarkRequest request);
}
