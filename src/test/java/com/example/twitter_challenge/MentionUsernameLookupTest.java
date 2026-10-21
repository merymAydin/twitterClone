package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The username lookup of the mention detection has to work on a Turkish locale.
 *
 * <p>Spring Data's collection based {@code InIgnoreCase} uppercases the values in Java with the
 * default locale of the JVM. On a Turkish locale that turns {@code i} into {@code İ} while the
 * database side {@code upper()} produces {@code I}, so a username containing an {@code i} would
 * never be found and every mention of such a user would be silently dropped.</p>
 */
@DataJpaTest
class MentionUsernameLookupTest {

    private static final Locale TURKISH = new Locale("tr", "TR");

    @Autowired
    private UserRepository userRepository;

    private Locale defaultLocale;

    @BeforeEach
    void useTurkishDefaultLocale() {
        defaultLocale = Locale.getDefault();
        Locale.setDefault(TURKISH);
    }

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    void findsAUsernameThatContainsALowercaseI() {
        User user = persistUser("alice");

        List<User> found = userRepository.findAllByUserNameIgnoreCaseIn(List.of("alice"));

        assertEquals(1, found.size());
        assertEquals(user.getId(), found.get(0).getId());
    }

    @Test
    void findsAUsernameWithADotlessI() {
        User user = persistUser("ilkay");

        List<User> found = userRepository.findAllByUserNameIgnoreCaseIn(List.of("ilkay"));

        assertEquals(1, found.size());
        assertEquals(user.getId(), found.get(0).getId());
    }

    @Test
    void findsAUsernameThatContainsADotlessI() {
        User user = persistUser("ilkay");

        List<User> found = userRepository.findAllByUserNameIgnoreCaseIn(List.of("ilkay"));

        assertEquals(1, found.size());
        assertEquals(user.getId(), found.get(0).getId());
    }

    @Test
    void findsAUsernameThatContainsAnUppercaseI() {
        // The database side lower() decides this case: PostgreSQL (the production database) is
        // locale independent, the H2 test database follows the locale of the JVM. The lookup must
        // not depend on that, so the username is additionally matched by its exact spelling.
        User user = persistUser("Ilker");

        List<User> found = userRepository.findAllByUserNameIgnoreCaseIn(
                List.of("ilker", "Ilker")
        );

        assertEquals(1, found.size());
        assertEquals(user.getId(), found.get(0).getId());
    }

    @Test
    void findsAUsernameWithoutAnyLetterI() {
        User user = persistUser("bob");

        List<User> found = userRepository.findAllByUserNameIgnoreCaseIn(List.of("bob"));

        assertEquals(1, found.size());
        assertEquals(user.getId(), found.get(0).getId());
    }

    @Test
    void resolvesSeveralUsernamesWithOneQuery() {
        User alice = persistUser("alice");
        User bob = persistUser("bob");

        List<User> found = userRepository.findAllByUserNameIgnoreCaseIn(List.of("alice", "bob"));

        assertEquals(2, found.size());
        assertEquals(List.of(alice.getId(), bob.getId()),
                found.stream().map(User::getId).toList());
    }

    @Test
    void ignoresAnUnknownUsername() {
        persistUser("alice");

        List<User> found = userRepository.findAllByUserNameIgnoreCaseIn(
                List.of("alice", "does_not_exist")
        );

        assertEquals(1, found.size());
    }

    @Test
    void ignoresASoftDeletedUser() {
        User user = persistUser("alice");
        user.setIsDeleted(true);
        userRepository.saveAndFlush(user);

        List<User> found = userRepository.findAllByUserNameIgnoreCaseIn(List.of("alice"));

        assertTrue(found.isEmpty());
    }

    private User persistUser(String userName) {
        User user = new User();
        user.setUserName(userName);
        user.setEmail(userName + "@example.com");
        user.setPassword("password");
        user.setIsDeleted(false);
        return userRepository.saveAndFlush(user);
    }
}
