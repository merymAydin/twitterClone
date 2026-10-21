package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findFirstByEmailAndIsDeletedFalseOrderByIdAsc(String email);

    Optional<User> findFirstByEmailIgnoreCaseAndIsDeletedFalseOrderByIdAsc(String email);

    Optional<User> findByUserName(String userName);

    Optional<User> findByUserNameAndIsDeletedFalse(String userName);

    /**
     * Resolves several usernames at once, ignoring the case. Used by the mention detection so a
     * content with many mentions costs a single query.
     *
     * <p>The query is written explicitly on purpose. Spring Data's {@code InIgnoreCase} uppercases
     * the values in Java with the default locale of the JVM. On a Turkish locale that turns
     * {@code i} into {@code İ}, while the database side {@code upper()} produces {@code I}, so a
     * username containing an {@code i} would never be found. The values are expected to be
     * lowercased with {@link java.util.Locale#ROOT} and the comparison runs in the database,
     * which is locale independent.</p>
     */
    @Query("""
            select u from User u
            where (u.userName in :userNames or lower(u.userName) in :userNames)
              and u.isDeleted = false
            order by u.id asc
            """)
    List<User> findAllByUserNameIgnoreCaseIn(
            Collection<String> userNames
    );

    Optional<User> findByIdAndIsDeletedFalse(Long id);

    Optional<User> findByStripeCustomerId(String stripeCustomerId);

    List<User> findAllByStripeCustomerId(String stripeCustomerId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByUserNameIgnoreCase(String userName);

    boolean existsByUserNameIgnoreCaseAndIdNot(String userName, Long id);

    Page<User> findByUserNameContainingIgnoreCaseAndIsDeletedFalse(String userName, Pageable pageable);

    List<User> findByIsDeletedFalse();
}
