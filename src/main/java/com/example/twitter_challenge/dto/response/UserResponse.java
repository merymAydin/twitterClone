package com.example.twitter_challenge.dto.response;

import com.example.twitter_challenge.Utils.Commons.Location;

import java.util.Date;

/**
 * Private account representation. {@code premium} is derived from the persisted Stripe
 * subscription state; it is not a client-writable field and never becomes true before an
 * entitled subscription exists.
 */
public record UserResponse(
        Long userId,
        String username,
        String email,
        Date birthday,
        Location location,
        String bio,
        String photo,
        String banner,
        boolean premium
) {
}
