package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Follower;
import com.example.twitter_challenge.Repository.FollowerRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Service.interfaces.FeedService;
import com.example.twitter_challenge.dto.response.TweetResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class FeedServiceImpl implements FeedService {
    private final TweetRepository tweetRepository;
    private final FollowerRepository followerRepository;
    private final TweetResponseMapper tweetResponseMapper;

    public FeedServiceImpl(
            TweetRepository tweetRepository,
            FollowerRepository followerRepository,
            TweetResponseMapper tweetResponseMapper
    ) {
        this.tweetRepository = tweetRepository;
        this.followerRepository = followerRepository;
        this.tweetResponseMapper = tweetResponseMapper;
    }

    @Transactional(readOnly = true)
    @Override
    public Page<TweetResponse> getTweets(Long userId, Pageable pageable) {
        List<Long> authorIds = new ArrayList<>();
        authorIds.add(userId);
        for (Follower follower : followerRepository.findByFollowerId(userId)) {
            Long followingId = follower.getFollowing().getId();
            if (!Boolean.TRUE.equals(follower.getFollowing().getIsDeleted())) {
                authorIds.add(followingId);
            }
        }

        return tweetResponseMapper.toResponsePage(
                tweetRepository.findVisibleByUserIdIn(authorIds, pageable)
        );
    }

    /**
     * Explore feed. It is deliberately independent of the follow graph: every visible tweet of
     * every active user is returned, newest first.
     */
    @Transactional(readOnly = true)
    @Override
    public Page<TweetResponse> getExploreTweets(Pageable pageable) {
        return tweetResponseMapper.toResponsePage(
                tweetRepository.findExplore(withoutSort(pageable))
        );
    }

    /**
     * The Explore order is defined by the query itself. A sort supplied by the client is
     * dropped, otherwise Spring Data would append it to the query order and the pagination
     * would stop being deterministic.
     */
    private Pageable withoutSort(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }
}
