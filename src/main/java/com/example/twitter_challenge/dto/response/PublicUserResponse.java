package com.example.twitter_challenge.dto.response;

import com.example.twitter_challenge.Utils.Commons.Location;

/**
 * Public profile representation. Email and birthday are intentionally excluded so public
 * user listing/search endpoints cannot leak private account data. Premium is a public badge
 * derived from the persisted subscription state.
 */
public record PublicUserResponse(
        Long userId,
        String username,
        Location location,
        String bio,
        String photo,
        String banner,
        boolean premium
) {
}
