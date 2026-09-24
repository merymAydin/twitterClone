package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Bookmark;
import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.BookmarkRepository;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.BookmarkService;
import com.example.twitter_challenge.dto.request.CreateBookmarkRequest;
import com.example.twitter_challenge.dto.response.BookmarkResponse;
import com.example.twitter_challenge.dto.response.TweetResponse;
import com.example.twitter_challenge.exception.*;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookmarkServiceImpl implements BookmarkService {
    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final TweetRepository tweetRepository;
    private final StatisticsRepository statisticsRepository;

    public BookmarkServiceImpl(BookmarkRepository bookmarkRepository, UserRepository userRepository, TweetRepository tweetRepository, StatisticsRepository statisticsRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.userRepository = userRepository;
        this.tweetRepository = tweetRepository;
        this.statisticsRepository = statisticsRepository;
    }

    @Transactional
    @Override
    public BookmarkResponse createBookmark(CreateBookmarkRequest request) {
        String curName = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUserName(curName)
                .orElseThrow(() ->
                        new UserNotFoundException("User with " + curName + " not found")
                );

        Optional<Bookmark> existingBookmark =
                bookmarkRepository.findByTweetIdAndUserId(
                        request.tweetId(),
                        user.getId()
                );

        if (existingBookmark.isPresent()) {
            throw new BookmarkAlreadyExistsException(
                    "User " + user.getId() + " has already bookmarked tweet " + request.tweetId()
            );
        }
        Tweet tweet = tweetRepository.findById(request.tweetId()).orElseThrow(()-> new TweetNotFoundException("Tweet with" + request.tweetId()+ "not found"));


        Bookmark bookmark = new Bookmark(user,tweet);
        Bookmark savedBookmark = bookmarkRepository.save(bookmark);
        Statistics statistics = statisticsRepository.findByTweetId(savedBookmark.getTweet().getId()).orElseThrow(()->new RuntimeException("Statistics for tweet " + savedBookmark.getTweet().getId() + " not found"));
        statistics.setBookmarks(statistics.getBookmarks() + 1);
        statisticsRepository.save(statistics);
        return new BookmarkResponse(savedBookmark.getUser().getId(), savedBookmark.getTweet().getId());
    }

    @Transactional
    @Override
    public void removeBookmark(CreateBookmarkRequest request,Long userId) {
        Bookmark bookmark = bookmarkRepository.findByTweetIdAndUserId(
                request.tweetId(),
                userId
        ).orElseThrow(()->
                new BookmarkNotFoundException("Bookmark with" + request.tweetId() + " not found"));

        Statistics statistics = statisticsRepository.findByTweetId(bookmark.getTweet().getId()).orElseThrow(()->new StatisticsNotFoundException("Statistics for tweet " + bookmark.getTweet().getId() + " not found"));
        statistics.setBookmarks(statistics.getBookmarks() - 1);
        statisticsRepository.save(statistics);
        bookmarkRepository.delete(bookmark);
    }

    @Override
    public List<BookmarkResponse> findAllBookmarks() {
        return bookmarkRepository.findAll()
                .stream()
                .map(bookmark -> new BookmarkResponse(
                        bookmark.getUser().getId(),
                        bookmark.getTweet().getId()
                ))
                .toList();
    }

    @Override
    public BookmarkResponse findBookmarkById(Long id) {
        Bookmark bookmark = bookmarkRepository.findById(id).orElseThrow(()-> new BookmarkNotFoundException("Bookmark with tweet id" + id + " not found"));
        return new  BookmarkResponse(bookmark.getUser().getId(), bookmark.getTweet().getId());
    }



}


