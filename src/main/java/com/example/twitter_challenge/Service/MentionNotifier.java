package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.NotificationType;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.NotificationService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the users that are mentioned in a content and notifies them.
 *
 * <p>The rules are the same for a tweet, a quote, an edited tweet and a comment:</p>
 * <ul>
 *   <li>a mention is {@code @username}; the username charset is the one of the registration
 *       validation ({@code [A-Za-z0-9_]})</li>
 *   <li>the same user mentioned several times in one content gets exactly one notification</li>
 *   <li>the author never notifies themselves</li>
 *   <li>usernames that do not belong to an active user are ignored</li>
 *   <li>recipients that already received a notification for the same action are skipped</li>
 * </ul>
 *
 * <p>The mentioned users are resolved with a single query, so a content with many mentions does
 * not turn into an N+1 query pattern.</p>
 */
@Component
public class MentionNotifier {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_]+)");

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public MentionNotifier(UserRepository userRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * Creates a {@link NotificationType#MENTION} notification for every user mentioned in the
     * content.
     *
     * @param content                  the content to scan for mentions
     * @param author                   the author of the content; never notified
     * @param message                  the notification message
     * @param alreadyNotifiedRecipient recipients that must not be notified again
     */
    public void notifyMentionedUsers(
            String content,
            User author,
            String message,
            Collection<Long> alreadyNotifiedRecipient
    ) {
        if (content == null || content.isBlank() || author == null || author.getId() == null) {
            return;
        }

        Set<String> usernames = extractUsernames(content);
        if (usernames.isEmpty()) {
            return;
        }

        List<User> mentioned = userRepository.findAllByUserNameIgnoreCaseIn(
                lowercaseAll(usernames)
        );
        if (mentioned == null || mentioned.isEmpty()) {
            return;
        }

        // Usernames are unique case insensitively, but old data can still hold a duplicate.
        // The lowest id wins so a mention always resolves to the same user.
        Map<String, User> byUsername = new LinkedHashMap<>();
        for (User user : mentioned) {
            if (user.getUserName() != null) {
                byUsername.putIfAbsent(user.getUserName().toLowerCase(Locale.ROOT), user);
            }
        }

        for (String username : usernames) {
            User user = byUsername.get(username.toLowerCase(Locale.ROOT));
            if (user == null || user.getId() == null) {
                // Unknown username: ignored on purpose.
                continue;
            }
            if (user.getId().equals(author.getId())) {
                // Self mention: never notified.
                continue;
            }
            if (alreadyNotifiedRecipient != null && alreadyNotifiedRecipient.contains(user.getId())) {
                // The recipient already knows about this action.
                continue;
            }
            notificationService.create(
                    author.getId(),
                    user.getId(),
                    NotificationType.MENTION,
                    message
            );
        }
    }

    /**
     * The distinct usernames of the content. The comparison is case insensitive so
     * {@code @Bob} and {@code @bob} count as one mention of the same user.
     */
    private Set<String> extractUsernames(String content) {
        Set<String> usernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            usernames.add(matcher.group(1));
        }
        return usernames;
    }

    /**
     * The usernames for the lookup: every spelling that is written in the content plus its lower
     * case form.
     *
     * <p>Both spellings are needed. The exact spelling matches a stored username whatever the
     * database collation does, the lower case form matches a username that was stored in another
     * case. {@link Locale#ROOT} matters: with the Turkish default locale of the JVM a plain
     * {@code toLowerCase()} would turn {@code I} into {@code ı} and the comparison would never
     * match.</p>
     */
    private List<String> lowercaseAll(Collection<String> usernames) {
        Set<String> spellings = new LinkedHashSet<>();
        for (String name : usernames) {
            if (name == null) {
                continue;
            }
            spellings.add(name);
            spellings.add(name.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(spellings);
    }
}
