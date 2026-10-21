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
import com.example.twitter_challenge.exception.BookmarkAlreadyExistsException;
import com.example.twitter_challenge.exception.BookmarkNotFoundException;
import com.example.twitter_challenge.exception.StatisticsNotFoundException;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class BookmarkServiceImpl implements BookmarkService {
    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final TweetRepository tweetRepository;
    private final StatisticsRepository statisticsRepository;

    public BookmarkServiceImpl(
            BookmarkRepository bookmarkRepository,
            UserRepository userRepository,
            TweetRepository tweetRepository,
            StatisticsRepository statisticsRepository
    ) {
        this.bookmarkRepository = bookmarkRepository;
        this.userRepository = userRepository;
        this.tweetRepository = tweetRepository;
        this.statisticsRepository = statisticsRepository;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private void adjustBookmarks(Long tweetId, long delta) {
        Statistics statistics = statisticsRepository.findByTweetIdForUpdate(tweetId)
                .orElseThrow(() -> new StatisticsNotFoundException(
                        "Statistics for tweet " + tweetId + " not found"
                ));
        statistics.setBookmarks(
                Math.max(0L, (statistics.getBookmarks() == null ? 0L : statistics.getBookmarks()) + delta)
        );
        statisticsRepository.save(statistics);
    }

    @Transactional
    @Override
    public BookmarkResponse createBookmark(CreateBookmarkRequest request) {
        User user = currentUser();
        Tweet tweet = tweetRepository.findVisibleById(request.tweetId())
                .orElseThrow(() -> new TweetNotFoundException(
                        "Tweet with " + request.tweetId() + " not found"
                ));

        Optional<Bookmark> existing =
                bookmarkRepository.findByTweetIdAndUserId(request.tweetId(), user.getId());
        Bookmark bookmark;
        if (existing.isPresent()) {
            bookmark = existing.get();
            if (!Boolean.TRUE.equals(bookmark.getIsDeleted())) {
                throw new BookmarkAlreadyExistsException(
                        "User " + user.getId() + " has already bookmarked tweet " + request.tweetId()
                );
            }
            // The unique constraint includes soft-deleted rows, so reactivate rather than insert.
            bookmark.setIsDeleted(false);
        } else {
            bookmark = new Bookmark(user, tweet);
        }

        try {
            bookmarkRepository.saveAndFlush(bookmark);
        } catch (DataIntegrityViolationException exception) {
            throw new BookmarkAlreadyExistsException(
                    "User " + user.getId() + " has already bookmarked tweet " + request.tweetId()
            );
        }

        adjustBookmarks(tweet.getId(), 1);
        return new BookmarkResponse(user.getId(), tweet.getId());
    }

    @Transactional
    @Override
    public void removeBookmark(CreateBookmarkRequest request, Long userId) {
        Bookmark bookmark = bookmarkRepository
                .findByTweetIdAndUserIdAndIsDeletedFalse(request.tweetId(), userId)
                .orElseThrow(() -> new BookmarkNotFoundException(
                        "Bookmark for tweet " + request.tweetId() + " not found"
                ));

        adjustBookmarks(bookmark.getTweet().getId(), -1);
        bookmark.setIsDeleted(true);
        bookmarkRepository.save(bookmark);
    }

    @Transactional(readOnly = true)
    @Override
    public List<BookmarkResponse> findAllBookmarks(Long userId) {
        return bookmarkRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .filter(bookmark -> !Boolean.TRUE.equals(bookmark.getTweet().getIsDeleted()))
                .filter(bookmark -> !Boolean.TRUE.equals(bookmark.getTweet().getUser().getIsDeleted()))
                .map(bookmark -> new BookmarkResponse(
                        bookmark.getUser().getId(),
                        bookmark.getTweet().getId()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public BookmarkResponse findBookmarkById(Long id, Long userId) {
        Bookmark bookmark = bookmarkRepository.findByIdAndUserIdAndIsDeletedFalse(id, userId)
                .orElseThrow(() -> new BookmarkNotFoundException("Bookmark " + id + " not found"));
        return new BookmarkResponse(bookmark.getUser().getId(), bookmark.getTweet().getId());
    }
}
