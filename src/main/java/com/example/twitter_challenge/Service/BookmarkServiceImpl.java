package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Bookmark;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.BookmarkRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.dto.request.CreateBookmarkRequest;
import com.example.twitter_challenge.dto.response.BookmarkResponse;
import com.example.twitter_challenge.exception.BookmarkAlreadyExistsException;
import com.example.twitter_challenge.exception.LikeAlreadyExistsException;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class BookmarkServiceImpl implements BookmarkService {
    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final TweetRepository tweetRepository;
    public BookmarkServiceImpl(BookmarkRepository bookmarkRepository, UserRepository userRepository, TweetRepository tweetRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.userRepository = userRepository;
        this.tweetRepository = tweetRepository;
    }
    @Override
    public BookmarkResponse createBookMark(CreateBookmarkRequest request) {
        Optional<Bookmark> existingBookmark = bookmarkRepository.findByTweetIdAndUserId(request.tweetId(), request.userId());
        if (existingBookmark.isPresent()) {
            throw new BookmarkAlreadyExistsException(
                    "User " + request.userId() + " has already bookmarked tweet " + request.tweetId()
            );
        }
        Tweet tweet = tweetRepository.findById(request.tweetId()).orElseThrow(()-> new TweetNotFoundException("Tweet with" + request.tweetId()+ "not found"));
        User user = userRepository.findById(request.userId())
                .orElseThrow(() ->
                        new UserNotFoundException("User with " + request.userId() + " not found")
                );
        Bookmark bookmark = new Bookmark(user,tweet);
        Bookmark savedBookmark = bookmarkRepository.save(bookmark);
        return new BookmarkResponse(savedBookmark.getUser().getId(), savedBookmark.getTweet().getId());




    }
}



//Bookmark oluştur
//        ↓
//save
//        ↓
//BookmarkResponse