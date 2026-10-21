package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Tweet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TweetRepository extends JpaRepository<Tweet, Long> {

    /**
     * Everything a tweet response needs: the author, the counters, and the original tweet of a
     * retweet or a quote including the original author. The original is fetched in the same
     * query so a list of tweets never triggers an extra query per tweet.
     */
    @EntityGraph(attributePaths = {
            "user", "statistics", "parent", "parent.user", "parent.statistics"
    })
    @Query("""
            select t from Tweet t
            where t.isDeleted = false and t.user.isDeleted = false
            """)
    Page<Tweet> findVisible(Pageable pageable);

    @EntityGraph(attributePaths = {
            "user", "statistics", "parent", "parent.user", "parent.statistics"
    })
    @Query("""
            select t from Tweet t
            where t.id = :id and t.isDeleted = false and t.user.isDeleted = false
            """)
    Optional<Tweet> findVisibleById(@Param("id") Long id);

    /**
     * Explore feed: every visible tweet of every active user, newest first.
     *
     * <p>The order is {@code createdAt desc nulls last, id desc}. The id is the tie breaker so
     * the total order is deterministic and pagination cannot repeat or skip a tweet.
     * {@code nulls last} keeps tweets that were stored before the creation timestamp existed
     * (their {@code createdAt} is null) behind the dated tweets instead of in front of them.
     * The Pageable is expected to be unsorted, otherwise its sort would be appended to this
     * order.</p>
     */
    @EntityGraph(attributePaths = {
            "user", "statistics", "parent", "parent.user", "parent.statistics"
    })
    @Query("""
            select t from Tweet t
            where t.isDeleted = false and t.user.isDeleted = false
            order by t.createdAt desc nulls last, t.id desc
            """)
    Page<Tweet> findExplore(Pageable pageable);

    @EntityGraph(attributePaths = {
            "user", "statistics", "parent", "parent.user", "parent.statistics"
    })
    @Query("""
            select t from Tweet t
            where lower(t.content) like lower(concat('%', :keyword, '%'))
              and t.isDeleted = false and t.user.isDeleted = false
            """)
    Page<Tweet> findVisibleByContentContaining(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "user", "statistics", "parent", "parent.user", "parent.statistics"
    })
    @Query("""
            select t from Tweet t
            where t.user.id in :userIds
              and t.isDeleted = false and t.user.isDeleted = false
            """)
    Page<Tweet> findVisibleByUserIdIn(
            @Param("userIds") List<Long> userIds,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "user", "statistics", "parent", "parent.user", "parent.statistics"
    })
    @Query("""
            select t from Tweet t
            where t.user.id = :userId
              and t.isDeleted = false and t.user.isDeleted = false
            """)
    Page<Tweet> findVisibleByUserId(
            @Param("userId") Long userId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "user", "statistics", "parent", "parent.user", "parent.statistics"
    })
    Optional<Tweet> findByParentIdAndUserIdAndContentIsNullAndIsDeletedFalse(
            Long parentId,
            Long userId
    );

    @EntityGraph(attributePaths = {
            "user", "statistics", "parent", "parent.user", "parent.statistics"
    })
    Optional<Tweet> findByParentIdAndUserIdAndContentIsNull(Long parentId, Long userId);

    List<Tweet> findByParentIdAndIsDeletedFalse(Long parentId);

    /**
     * The ids of the original tweets the given user has retweeted.
     *
     * <p>A retweet is a tweet with a parent and without content of its own. Soft deleted
     * retweets and retweets of a deleted original are not counted.</p>
     */
    @Query("""
            select t.parent.id from Tweet t
            where t.user.id = :userId
              and t.parent is not null
              and t.content is null
              and t.isDeleted = false
              and t.parent.isDeleted = false
              and t.parent.user.isDeleted = false
            """)
    List<Long> findRetweetedOriginalTweetIds(@Param("userId") Long userId);
}
