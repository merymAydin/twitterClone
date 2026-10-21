package com.example.twitter_challenge.dto.request;

import com.example.twitter_challenge.Utils.Commons.Location;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Date;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 30)
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "must contain only letters, numbers and underscores")
        String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Past Date birthday,
        Location location,
        @Size(max = 255) String bio,
        @Size(max = 255) String photo,
        @Size(max = 255) String banner
) {
}
