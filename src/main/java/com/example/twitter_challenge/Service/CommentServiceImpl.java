package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Comment;
import com.example.twitter_challenge.Entity.NotificationType;
import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.CommentRepository;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.CommentService;
import com.example.twitter_challenge.Service.interfaces.NotificationService;
import com.example.twitter_challenge.dto.request.CreateCommentRequest;
import com.example.twitter_challenge.dto.request.UpdateCommentRequest;
import com.example.twitter_challenge.dto.response.CommentResponse;
import com.example.twitter_challenge.exception.CommentNotFound;
import com.example.twitter_challenge.exception.ForbiddenException;
import com.example.twitter_challenge.exception.StatisticsNotFoundException;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class CommentServiceImpl implements CommentService {
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final TweetRepository tweetRepository;
    private final StatisticsRepository statisticsRepository;
    private final NotificationService notificationService;
    private final MentionNotifier mentionNotifier;

    public CommentServiceImpl(
            CommentRepository commentRepository,
            UserRepository userRepository,
            TweetRepository tweetRepository,
            StatisticsRepository statisticsRepository,
            NotificationService notificationService,
            MentionNotifier mentionNotifier
    ) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.tweetRepository = tweetRepository;
        this.statisticsRepository = statisticsRepository;
        this.notificationService = notificationService;
        this.mentionNotifier = mentionNotifier;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private void adjustComments(Long tweetId, long delta) {
        Statistics statistics = statisticsRepository.findByTweetIdForUpdate(tweetId)
                .orElseThrow(() -> new StatisticsNotFoundException(
                        "Statistics for tweet " + tweetId + " not found"
                ));
        statistics.setComments(
                Math.max(0L, (statistics.getComments() == null ? 0L : statistics.getComments()) + delta)
        );
        statisticsRepository.save(statistics);
    }

    private CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getUser().getId(),
                comment.getTweet().getId(),
                comment.getContent()
        );
    }

    @Transactional
    @Override
    public CommentResponse createComment(CreateCommentRequest request) {
        User user = currentUser();
        Tweet tweet = tweetRepository.findVisibleById(request.tweetId())
                .orElseThrow(() -> new TweetNotFoundException(
                        "Tweet with " + request.tweetId() + " not found"
                ));

        Comment comment = new Comment(tweet, user, request.content());
        Comment savedComment = commentRepository.save(comment);
        adjustComments(tweet.getId(), 1);

        if (!tweet.getUser().getId().equals(user.getId())) {
            notificationService.create(
                    user.getId(),
                    tweet.getUser().getId(),
                    NotificationType.COMMENT,
                    "Commented on your post"
            );
        }

        // The author of the commented tweet already received a comment notification, so they are
        // not notified a second time when they are mentioned in the same comment.
        mentionNotifier.notifyMentionedUsers(
                request.content(),
                user,
                "You were mentioned in a comment",
                Set.of(tweet.getUser().getId())
        );

        return toResponse(savedComment);
    }

    @Transactional
    @Override
    public void removeComment(Long id, Long userId) {
        Comment comment = commentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new CommentNotFound("Comment with " + id + " not found"));
        if (!comment.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You are not allowed to remove this comment");
        }
        adjustComments(comment.getTweet().getId(), -1);
        comment.setIsDeleted(true);
        commentRepository.save(comment);
    }

    @Transactional(readOnly = true)
    @Override
    public List<CommentResponse> findAllComments() {
        return commentRepository.findAll().stream()
                .filter(comment -> !Boolean.TRUE.equals(comment.getIsDeleted()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<CommentResponse> findByTweetId(Long tweetId) {
        tweetRepository.findVisibleById(tweetId)
                .orElseThrow(() -> new TweetNotFoundException("Tweet with " + tweetId + " not found"));
        return commentRepository.findByTweetIdAndIsDeletedFalseOrderByCreatedAtAsc(tweetId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public CommentResponse findCommentById(Long id) {
        Comment comment = commentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new CommentNotFound("Comment " + id + " not found"));
        return toResponse(comment);
    }

    @Transactional
    @Override
    public CommentResponse update(Long commentId, UpdateCommentRequest request, Long userId) {
        Comment comment = commentRepository.findByIdAndIsDeletedFalse(commentId)
                .orElseThrow(() -> new CommentNotFound("Comment " + commentId + " not found"));
        if (!comment.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You are not allowed to update this comment");
        }
        comment.setContent(request.content());
        commentRepository.save(comment);
        return toResponse(comment);
    }
}
