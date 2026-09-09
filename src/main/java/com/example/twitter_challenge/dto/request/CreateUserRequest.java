package com.example.twitter_challenge.dto.request;

import com.example.twitter_challenge.Utils.Commons.Location;
import jakarta.validation.constraints.*;

import java.util.Date;

public record CreateUserRequest(@NotBlank String username,@NotBlank @Email String email,@NotBlank String password,@Past Date birthday, Location location, String bio, String photo, String banner) {
}
