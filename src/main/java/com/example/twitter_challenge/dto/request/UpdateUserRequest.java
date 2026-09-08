package com.example.twitter_challenge.dto.request;

import com.example.twitter_challenge.Utils.Commons.Location;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;

import java.util.Date;

public record UpdateUserRequest(@NotBlank String username, @Email String email, @Past Date birthday, Location location, String bio, String photo, String banner) {
}
